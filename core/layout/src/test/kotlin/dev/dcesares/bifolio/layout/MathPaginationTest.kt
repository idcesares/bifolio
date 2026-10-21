package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SYN-12 and PAG-07 through [MathSizeProvider]; the test column is 200 px wide and 200 px tall (cap 170 px). */
class MathPaginationTest {
    private fun display(name: String) = "\$\$$name\$\$"

    private fun sizes(vararg known: Pair<String, MathSize>): MathSizeProvider {
        val map = known.toMap()
        return MathSizeProvider { map[it.tex] ?: MathSize.Unknown }
    }

    private fun known(
        w: Int,
        h: Int,
    ) = MathSize.Known(w, h)

    /** Box of the only atomic line of block [index] laid out at 200 px: (x, width, height). */
    private fun box(
        doc: Document,
        provider: MathSizeProvider,
        index: Int = 0,
    ): Triple<Int, Int, Int> {
        val flattener = BlockFlattener(doc, FakeBlockMeasurer(), testGeometry(), ImageSizeProvider.UNKNOWN, provider)
        val layout = flattener.layout(index, 200)
        assertEquals(1, layout.lineCount)
        assertEquals(LineKind.ATOMIC, layout.draw.kind[0])
        return Triple(layout.draw.x[0], layout.draw.width[0], layout.height(0, 1))
    }

    @Test
    fun `PAG-07 a wide formula scales down to the column and keeps its aspect ratio`() {
        val doc = MarkdownParser.parse(markdown(display("a")))
        assertTrue(doc.blocks.single() is MathBlock)
        assertEquals(Triple(0, 200, 20), box(doc, sizes("a" to known(1000, 100))))
    }

    @Test
    fun `PAG-07 a small formula is never upscaled and is centered`() {
        val doc = MarkdownParser.parse(markdown(display("a")))
        assertEquals(Triple(75, 50, 40), box(doc, sizes("a" to known(50, 40))))
    }

    @Test
    fun `PAG-07 a tall formula is capped at 85 percent of the page height`() {
        val doc = MarkdownParser.parse(markdown(display("a")))
        assertEquals(Triple(91, 17, 170), box(doc, sizes("a" to known(100, 1000))))
    }

    @Test
    fun `PAG-07 a formula in a list item is drawn as that block inside the indent`() {
        val doc = MarkdownParser.parse("- ${display("a")}\n")
        val geometry = testGeometry()
        val provider = sizes("a" to known(100, 50))
        val flow = Paginator(doc, FakeBlockMeasurer(), geometry, math = provider).paginateAll()!!
        val draw = PageDetailer(doc, FakeBlockMeasurer(), geometry, math = provider).detail(flow.pages[0], 0)
        val atomic = draw.items.filterIsInstance<DrawAtomic>().single()
        assertTrue(atomic.block is MathBlock)
        assertEquals(50, atomic.height)
        assertTrue(atomic.x >= 20, "inside the list indent")
    }

    @Test
    fun `SYN-12 an unknown size gives the measurer placeholder box, the same for every failure`() {
        val doc = MarkdownParser.parse(markdown(display("a"), display("b")))
        val provider = sizes("a" to MathSize.Unknown)
        assertEquals(Triple(0, 200, 100), box(doc, provider, 0))
        assertEquals(box(doc, provider, 0), box(doc, MathSizeProvider.UNKNOWN, 1))
    }

    @Test
    fun `PAG-07 a formula that does not fit moves whole to the next page and leaves the space empty`() {
        val doc = MarkdownParser.parse(markdown(para(8), display("a"), para(1)))
        val provider = sizes("a" to known(200, 100))
        val flow = Paginator(doc, FakeBlockMeasurer(), testGeometry(), math = provider).paginateAll()!!
        assertEquals(listOf("0:0-8", "1:0-1 2:0-1"), flow.shape())
        assertEquals(160, flow.pages[0].usedHeight)
    }

    @Test
    fun `PAG-07 a formula that fits stays and text after it never reflows above it`() {
        val doc = MarkdownParser.parse(markdown(para(8), display("a"), para(1)))
        val provider = sizes("a" to known(200, 20))
        val flow = Paginator(doc, FakeBlockMeasurer(), testGeometry(), math = provider).paginateAll()!!
        assertEquals(listOf("0:0-8 1:0-1", "2:0-1"), flow.shape())
    }

    @Test
    fun `a fenced math block is atomic in the same way`() {
        val doc = MarkdownParser.parse(markdown(para(8), "```math\na\n```", para(1)))
        assertTrue(doc.blocks[1] is MathBlock)
        val provider = sizes("a" to known(200, 100))
        val flow = Paginator(doc, FakeBlockMeasurer(), testGeometry(), math = provider).paginateAll()!!
        assertEquals(listOf("0:0-8", "1:0-1 2:0-1"), flow.shape())
    }

    @Test
    fun `image sizes and formula sizes do not mix`() {
        val doc = MarkdownParser.parse(markdown("![a](a.png)", display("a")))
        val images = ImageSizeProvider { ImageSize.Known(10, 10) }
        val layout = BlockFlattener(doc, FakeBlockMeasurer(), testGeometry(), images, MathSizeProvider.UNKNOWN)
        assertEquals(10, layout.layout(0, 200).height(0, 1))
        assertEquals(100, layout.layout(1, 200).height(0, 1))
    }

    @Test
    fun `ANC-03 a formula size that arrives later relays out and the anchor is drawn on the spread found for it`() {
        val md = markdown(para(6), display("a"), para(7), display("b"), para(7), "## End", para(3))
        val doc = MarkdownParser.parse(md)
        val geometry = testGeometry()
        val measurer = FakeBlockMeasurer()
        val before = Paginator(doc, measurer, geometry).paginateAll()!!
        val provider = sizes("a" to known(300, 900), "b" to known(40, 40))
        val after = Paginator(doc, measurer, geometry, math = provider).paginateAll()!!
        assertTrue(before.shape() != after.shape(), "the sizes must change the pages for the test to mean something")
        val flattener = BlockFlattener(doc, measurer, geometry, ImageSizeProvider.UNKNOWN, provider)
        val anchors = (0 until before.pages.size).map { before.pages[it].anchor } + Anchor(1, 0) + Anchor(3, 0)
        for (anchor in anchors) {
            val seen = ArrayList<PageFlow>()
            Paginator(doc, measurer, geometry, math = provider).paginateAround(anchor, onProgress = { seen.add(it) })
            for ((i, flow) in seen.withIndex()) {
                val spread = flow.spreadIndexOf(anchor)
                assertTrue(spread >= 0, "$anchor snapshot $i")
                val page =
                    flow.pagesOfSpread(spread).first { p ->
                        p.slices.any { s ->
                            val line = flattener.layout(s.blockIndex, 200).lineAtOffset(anchor.charOffset)
                            s.blockIndex == anchor.blockIndex && line in s.startLine until s.endLine
                        }
                    }
                assertTrue(page.slices.any { it.blockIndex == anchor.blockIndex }, "$anchor drawn on spread $spread")
            }
        }
    }

    @Test
    fun `PAG-10 a flow made without formula sizes is not served once the sizes change`() {
        val doc = MarkdownParser.parse(markdown(para(6), display("a"), para(3)))
        val geometry = testGeometry()
        val service = PaginationService(LayoutCache())
        val measurer = FakeBlockMeasurer()
        val provider = sizes("a" to known(300, 900))
        val first = service.paginate("h", doc, measurer, geometry, Anchor(0, 0))!!
        assertSame(first, service.paginate("h", doc, measurer, geometry, Anchor(0, 0)))
        val sized = service.paginate("h", doc, measurer, geometry, Anchor(0, 0), math = provider, mathKey = 1)!!
        assertNotSame(first, sized)
        assertTrue(first.shape() != sized.shape())
        assertSame(sized, service.paginate("h", doc, measurer, geometry, Anchor(0, 0), math = provider, mathKey = 1))
        // Image and formula keys are independent parts of the cache key.
        val both =
            service.paginate("h", doc, measurer, geometry, Anchor(0, 0), imageKey = 1, math = provider, mathKey = 1)
        assertNotSame(sized, both)
    }

    @Test
    fun `inline math is laid out as a code styled run of its TeX source, one character per source character`() {
        val md = "Energy \$E = mc^2\$ here.\n"
        val doc = MarkdownParser.parse(md)
        val p = doc.blocks.single() as Paragraph
        val math = p.content.filterIsInstance<Inline.InlineMath>().single()
        val rendered = RenderedText.of(p.content, doc.source, p.range.start, p.range.end)
        assertEquals("Energy E = mc^2 here.", rendered.text)
        val at = rendered.text.indexOf(math.tex)
        for (i in math.tex.indices) assertEquals(math.range!!.start + i, rendered.map[at + i])
        var flagged = 0
        for (r in 0 until rendered.styles.count) {
            if (rendered.styles.flags[r] and StyleFlag.CODE != 0) {
                assertEquals(at, rendered.styles.starts[r])
                assertEquals(at + math.tex.length, rendered.styles.ends[r])
                flagged++
            }
        }
        assertEquals(1, flagged)
    }
}
