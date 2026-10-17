package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.SourceRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fixtures use [testGeometry]: pages of 10 lines (200 px), 20-character lines, a block gap of one line.
 * Expected shapes read `block:startLine-endLine` per slice.
 */
class PaginationRulesTest {
    @Test
    fun `a long paragraph fills pages and keeps lines in order (PAG-02)`() {
        val (_, flow) = paginate(markdown(para(25)))
        assertEquals(listOf("0:0-10", "0:10-20", "0:20-25"), flow.shape())
    }

    @Test
    fun `PAG-02 orphan control moves a block when only one line would stay`() {
        val (_, flow) = paginate(markdown(para(8), para(6)))
        assertEquals(listOf("0:0-8", "1:0-6"), flow.shape())
    }

    @Test
    fun `PAG-02 widow control leaves at least two lines on the next page`() {
        val (_, flow) = paginate(markdown(para(6), para(4)))
        assertEquals(listOf("0:0-6 1:0-2", "1:2-4"), flow.shape())
    }

    @Test
    fun `PAG-02 a split with exactly two lines on each side is allowed`() {
        val (_, flow) = paginate(markdown(para(7), para(4)))
        assertEquals(listOf("0:0-7 1:0-2", "1:2-4"), flow.shape())
    }

    @Test
    fun `PAG-02 a block of three lines or fewer is never split`() {
        val (_, flow) = paginate(markdown(para(7), para(3)))
        assertEquals(listOf("0:0-7", "1:0-3"), flow.shape())
    }

    @Test
    fun `PAG-03 a heading stays when two lines of the next block follow on the page`() {
        val (_, flow) = paginate(markdown(para(5), "## Title", para(5)))
        assertEquals(listOf("0:0-5 1:0-1 2:0-2", "2:2-5"), flow.shape())
    }

    @Test
    fun `PAG-03 a heading moves to the next page when fewer than two lines would follow`() {
        val (_, flow) = paginate(markdown(para(6), "## Title", para(5)))
        assertEquals(listOf("0:0-6", "1:0-1 2:0-5"), flow.shape())
    }

    @Test
    fun `PAG-03 counts the whole next block when it cannot be split`() {
        val (_, flow) = paginate(markdown(para(5), "## Title", para(3)))
        assertEquals(listOf("0:0-5", "1:0-1 2:0-3"), flow.shape())
    }

    @Test
    fun `PAG-03 a heading followed by a heading keeps both with the next text`() {
        val (_, flow) = paginate(markdown(para(5), "## One", "### Two", para(5)))
        assertEquals(listOf("0:0-5", "1:0-1 2:0-1 3:0-5"), flow.shape())
    }

    @Test
    fun `PAG-03 a heading at the end of the document stays where it is`() {
        val (_, flow) = paginate(markdown(para(8), "## Last"))
        assertEquals(listOf("0:0-8 1:0-1"), flow.shape())
    }

    @Test
    fun `PAG-04 code splits between lines at any line and marks its continuation`() {
        val code = "```\n" + (1..25).joinToString("\n") { "c%02d".format(it) } + "\n```"
        val (_, flow) = paginate(markdown(para(8), code))
        assertEquals(listOf("0:0-8 1:0-1", "1:1-11", "1:11-21", "1:21-25"), flow.shape())
        val slices = flow.pages.flatMap { it.slices }.filter { it.blockIndex == 1 }
        assertEquals(listOf(false, true, true, true), slices.map { it.isContinuation })
        assertEquals(listOf(true, true, true, false), slices.map { it.continuesOnNextPage })
    }

    @Test
    fun `PAG-05 a quote splits by line like a paragraph and keeps one block index`() {
        val (_, flow) = paginate(markdown("> " + nested(25)))
        assertEquals(listOf("0:0-10", "0:10-20", "0:20-25"), flow.shape())
    }

    @Test
    fun `PAG-06 a tall table splits between rows and repeats the header`() {
        val rows = (1..24).joinToString("\n") { "| r$it | v$it |" }
        val table = "| A | B |\n| --- | --- |\n$rows"
        val (_, flow) = paginate(markdown(table))
        assertEquals(listOf("0:0-10", "0:10-19", "0:19-25"), flow.shape())
        val second = flow.pages[1].slices.single()
        assertEquals(20, second.repeatedHeaderHeight)
        assertEquals(200, second.height)
        assertEquals(
            0,
            flow.pages[0]
                .slices
                .single()
                .repeatedHeaderHeight,
        )
    }

    @Test
    fun `PAG-06 the header never stays alone at the bottom of a page`() {
        val rows = (1..5).joinToString("\n") { "| r$it | v$it |" }
        val table = "| A | B |\n| --- | --- |\n$rows"
        val (_, flow) = paginate(markdown(para(8), table))
        assertEquals(listOf("0:0-8", "1:0-6"), flow.shape())
    }

    @Test
    fun `PAG-06 a row with wrapped cells is one unbreakable line`() {
        val long = "word ".repeat(6).trim()
        val table = "| A | B |\n| --- | --- |\n| $long | $long |"
        val (doc, flow) = paginate(markdown(table))
        assertEquals(listOf("0:0-2"), flow.shape())
        val layout = BlockFlattener(doc, FakeBlockMeasurer(), testGeometry()).layout(0, 200)
        assertEquals(2, layout.lineCount)
        assertTrue(layout.height(1, 2) > 20, "a wrapped row is taller than one line")
    }

    @Test
    fun `PAG-07 an atomic block that does not fit moves whole and leaves the space empty`() {
        val (doc, _) = paginate(markdown(para(8), para(2)))
        val withImage = doc.withImageAt(1)
        val flow = Paginator(withImage, FakeBlockMeasurer(), testGeometry()).paginateAll()!!
        assertEquals(listOf("0:0-8", "1:0-1 2:0-2"), flow.shape())
    }

    @Test
    fun `PAG-07 text after an atomic block does not reflow above it`() {
        val (doc, _) = paginate(markdown(para(8), para(1)))
        val flow = Paginator(doc.withImageAt(1), FakeBlockMeasurer(), testGeometry()).paginateAll()!!
        assertEquals(listOf("0:0-8", "1:0-1 2:0-1"), flow.shape())
        assertEquals(160, flow.pages[0].usedHeight)
    }

    @Test
    fun `PAG-07 an atomic block scales to the column and to 85 percent of the page`() {
        val (doc, _) = paginate(markdown(para(1)))
        val image = AtomicSize(400, 400)
        val flow =
            Paginator(
                doc.withImageAt(0),
                FakeBlockMeasurer(atomicSize = { image }),
                testGeometry(),
            ).paginateAll()!!
        // Column 200 px wide scales to 200 x 200, then the 85 percent cap of a 200 px page gives 170.
        assertEquals(
            170,
            flow.pages[0]
                .slices
                .first()
                .height,
        )
    }

    @Test
    fun `SYN-15 a horizontal rule is an ordinary unbreakable line and never forces a page break`() {
        val (_, flow) = paginate(markdown(para(2), "---", para(2)))
        assertEquals(listOf("0:0-2 1:0-1 2:0-2"), flow.shape())
    }

    @Test
    fun `PAG-08 list items break between items and split like paragraphs`() {
        val items = (1..12).joinToString("\n") { "- item $it" }
        assertEquals(listOf("0:0-10", "0:10-12"), paginate(items + "\n").second.shape())
    }

    @Test
    fun `PAG-08 a long list item keeps its marker line with at least one more line`() {
        val md = "- ${nested(8)}\n- ${nested(4)}\n"
        assertEquals(listOf("0:0-10", "0:10-12"), paginate(md).second.shape())
        val md3 = "- ${nested(8)}\n- ${nested(3)}\n"
        assertEquals(listOf("0:0-8", "0:8-11"), paginate(md3).second.shape())
    }

    @Test
    fun `PAG-12 a spread advances by the column count and the last spread may be short`() {
        val flow = paginate(markdown(para(25)), testGeometry(columns = 2)).second
        assertEquals(3, flow.pages.size)
        assertEquals(2, flow.spreadCount)
        assertEquals(2, flow.pagesOfSpread(0).size)
        assertEquals(1, flow.pagesOfSpread(1).size)
        assertEquals(listOf(false, false, true), flow.pages.map { it.endsDocument })
    }

    @Test
    fun `an empty document has no pages and a blank one has no slices`() {
        assertEquals(0, paginate("").second.pages.size)
        assertEquals(0, paginate("   \n\n").second.pages.size)
    }

    @Test
    fun `fallback a page shorter than the rules still paginates and terminates`() {
        val oneLine = paginate(markdown(para(5)), testGeometry(lines = 1)).second
        assertEquals(5, oneLine.pages.size)
        val short = paginate(markdown(para(5), "## H", para(5)), testGeometry(lines = 3)).second
        val total = short.pages.sumOf { p -> p.slices.sumOf { it.lineCount } }
        assertEquals(11, total)
        assertFalse(short.pages.any { it.slices.isEmpty() })
    }

    private fun Document.withImageAt(index: Int): Document {
        val source = source + "![alt](x.png)\n"
        val image = ImageBlock("x.png", null, "alt", SourceRange(this.source.length, source.length))
        val blocks = blocks.toMutableList()
        val moved = blocks.subList(index, blocks.size).toList()
        while (blocks.size > index) blocks.removeAt(blocks.size - 1)
        blocks.add(image)
        blocks.addAll(moved)
        return copy(source = source, blocks = blocks)
    }
}
