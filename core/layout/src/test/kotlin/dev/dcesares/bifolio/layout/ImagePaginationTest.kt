package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SYN-08 and PAG-07 through [ImageSizeProvider]; the test column is 200 px wide and 200 px tall (cap 170 px). */
class ImagePaginationTest {
    private fun img(name: String) = "![$name]($name.png)"

    private fun sizes(vararg known: Pair<String, ImageSize>): ImageSizeProvider {
        val map = known.associate { (name, size) -> "$name.png" to size }
        return ImageSizeProvider { map[it.source] ?: ImageSize.Unknown }
    }

    private fun known(
        w: Int,
        h: Int,
    ) = ImageSize.Known(w, h)

    /** Box of the only atomic line of block [index] laid out at 200 px: (x, width, height). */
    private fun box(
        doc: Document,
        provider: ImageSizeProvider,
        index: Int = 0,
    ): Triple<Int, Int, Int> {
        val layout = BlockFlattener(doc, FakeBlockMeasurer(), testGeometry(), provider).layout(index, 200)
        assertEquals(1, layout.lineCount)
        assertEquals(LineKind.ATOMIC, layout.draw.kind[0])
        return Triple(layout.draw.x[0], layout.draw.width[0], layout.height(0, 1))
    }

    @Test
    fun `PAG-07 a wide image scales down to the column and keeps its aspect ratio`() {
        val doc = MarkdownParser.parse(markdown(img("a")))
        assertTrue(doc.blocks.single() is ImageBlock)
        assertEquals(Triple(0, 200, 20), box(doc, sizes("a" to known(1000, 100))))
    }

    @Test
    fun `PAG-07 a small image is never upscaled and is centered`() {
        val doc = MarkdownParser.parse(markdown(img("a")))
        assertEquals(Triple(75, 50, 40), box(doc, sizes("a" to known(50, 40))))
    }

    @Test
    fun `PAG-07 a tall image is capped at 85 percent of the page height`() {
        val doc = MarkdownParser.parse(markdown(img("a")))
        assertEquals(Triple(91, 17, 170), box(doc, sizes("a" to known(100, 1000))))
        assertEquals(Triple(15, 170, 170), box(doc, sizes("a" to known(400, 400))))
    }

    @Test
    fun `PAG-07 an image in a list item is placed and drawn as that image inside the indent`() {
        val doc = MarkdownParser.parse("- ${img("a")}\n")
        val geometry = testGeometry()
        val provider = sizes("a" to known(100, 50))
        val flow = Paginator(doc, FakeBlockMeasurer(), geometry, provider).paginateAll()!!
        val draw = PageDetailer(doc, FakeBlockMeasurer(), geometry, provider).detail(flow.pages[0], 0)
        val atomic = draw.items.filterIsInstance<DrawAtomic>().single()
        assertTrue(atomic.block is ImageBlock, "the nested image itself, not its list")
        assertEquals(50, atomic.height)
        assertTrue(atomic.x >= 20, "inside the list indent")
    }

    @Test
    fun `SYN-08 an unknown size gives the measurer placeholder box, the same for every failure`() {
        val doc = MarkdownParser.parse(markdown(img("a"), img("b")))
        val provider = sizes("a" to ImageSize.Unknown)
        assertEquals(Triple(0, 200, 100), box(doc, provider, 0))
        assertEquals(box(doc, provider, 0), box(doc, ImageSizeProvider.UNKNOWN, 1))
    }

    @Test
    fun `PAG-07 an image that does not fit moves whole to the next page and leaves the space empty`() {
        val doc = MarkdownParser.parse(markdown(para(8), img("a"), para(1)))
        val flow = Paginator(doc, FakeBlockMeasurer(), testGeometry(), sizes("a" to known(200, 100))).paginateAll()!!
        assertEquals(listOf("0:0-8", "1:0-1 2:0-1"), flow.shape())
        assertEquals(160, flow.pages[0].usedHeight)
    }

    @Test
    fun `PAG-07 an image that fits stays and text after it never reflows above it`() {
        val doc = MarkdownParser.parse(markdown(para(8), img("a"), para(1)))
        val flow = Paginator(doc, FakeBlockMeasurer(), testGeometry(), sizes("a" to known(200, 20))).paginateAll()!!
        assertEquals(listOf("0:0-8 1:0-1", "2:0-1"), flow.shape())
    }

    @Test
    fun `PAG-07 an image taller than the page is scaled to the cap and alone on its page`() {
        val doc = MarkdownParser.parse(markdown(para(2), img("a"), para(1)))
        val provider = sizes("a" to known(100, 9000))
        val flow = Paginator(doc, FakeBlockMeasurer(), testGeometry(), provider).paginateAll()!!
        assertEquals(listOf("0:0-2", "1:0-1", "2:0-1"), flow.shape())
        assertEquals(170, flow.pages[1].usedHeight)
    }

    @Test
    fun `ANC-03 a size that arrives later relays out and the anchor is drawn on the spread found for it`() {
        val md = markdown(para(6), img("a"), para(7), img("b"), para(7), "## End", para(3))
        val doc = MarkdownParser.parse(md)
        val geometry = testGeometry()
        val measurer = FakeBlockMeasurer()
        val before = Paginator(doc, measurer, geometry).paginateAll()!!
        val provider = sizes("a" to known(300, 900), "b" to known(40, 40))
        val after = Paginator(doc, measurer, geometry, provider).paginateAll()!!
        assertTrue(before.shape() != after.shape(), "the sizes must change the pages for the test to mean something")
        val flattener = BlockFlattener(doc, measurer, geometry, provider)
        val anchors = (0 until before.pages.size).map { before.pages[it].anchor } + Anchor(1, 0) + Anchor(3, 0)
        for (anchor in anchors) {
            val seen = ArrayList<PageFlow>()
            Paginator(doc, measurer, geometry, provider).paginateAround(anchor, onProgress = { seen.add(it) })
            for ((i, flow) in seen.withIndex()) {
                val spread = flow.spreadIndexOf(anchor)
                assertTrue(spread >= 0, "$anchor snapshot $i")
                val page = flow.pagesOfSpread(spread).first { p -> p.slices.any { drawn(it, flattener, anchor) } }
                assertTrue(page.slices.any { it.blockIndex == anchor.blockIndex }, "$anchor drawn on spread $spread")
            }
        }
    }

    private fun drawn(
        slice: Slice,
        flattener: BlockFlattener,
        anchor: Anchor,
    ): Boolean {
        if (slice.blockIndex != anchor.blockIndex) return false
        val line = flattener.layout(slice.blockIndex, 200).lineAtOffset(anchor.charOffset)
        return line in slice.startLine until slice.endLine
    }

    @Test
    fun `PAG-10 a flow made without image sizes is not served once the sizes change`() {
        val doc = MarkdownParser.parse(markdown(para(6), img("a"), para(3)))
        val geometry = testGeometry()
        val service = PaginationService(LayoutCache())
        val measurer = FakeBlockMeasurer()
        val provider = sizes("a" to known(300, 900))
        val first = service.paginate("h", doc, measurer, geometry, Anchor(0, 0))!!
        val again = service.paginate("h", doc, measurer, geometry, Anchor(0, 0))!!
        assertSame(first, again)
        val sized = service.paginate("h", doc, measurer, geometry, Anchor(0, 0), images = provider, imageKey = 1)!!
        assertNotSame(first, sized)
        assertTrue(first.shape() != sized.shape())
        assertSame(sized, service.paginate("h", doc, measurer, geometry, Anchor(0, 0), images = provider, imageKey = 1))
    }

    @Test
    fun `fit never exceeds the bounds, never upscales and keeps the ratio within one pixel`() {
        val random = kotlin.random.Random(SEED)
        repeat(CASES) {
            val size = AtomicSize(random.nextInt(1, 6000), random.nextInt(1, 6000))
            val maxW = random.nextInt(1, 1500)
            val maxH = random.nextInt(1, 1500)
            val fit = AtomicFit.fit(size, maxW, maxH)
            val tag = "$size in ${maxW}x$maxH -> $fit"
            assertTrue(fit.width in 1..maxW && fit.height in 1..maxH, tag)
            assertTrue(fit.width <= size.width && fit.height <= size.height, "upscaled: $tag")
            val skew = kotlin.math.abs(fit.width.toLong() * size.height - fit.height.toLong() * size.width)
            assertTrue(skew <= maxOf(size.width, size.height), "ratio: $tag")
            if (size.width <= maxW && size.height <= maxH) assertEquals(size, fit, tag)
        }
    }

    private companion object {
        const val SEED = 20260808L
        const val CASES = 5000
    }
}
