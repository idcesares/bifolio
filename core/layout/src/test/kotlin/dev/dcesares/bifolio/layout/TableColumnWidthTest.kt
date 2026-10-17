package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PAG-06: a table column is never narrower than its longest word, otherwise the table scrolls sideways. */
class TableColumnWidthTest {
    private fun tableDraw(
        md: String,
        columnWidth: Int,
    ): TableDraw {
        val doc = MarkdownParser.parse(md)
        val geometry = testGeometry(columnWidth = columnWidth)
        val layout = BlockFlattener(doc, FakeBlockMeasurer(), geometry).layout(0, columnWidth)
        return layout.draw.table[0]!!
    }

    @Test
    fun `a column keeps room for its longest word when the table is squeezed`() {
        // Fake: 10 px per char. "Left" needs 40 px, the second column is long prose. Width 200 forces squeezing.
        val prose = "alpha beta gamma delta epsilon zeta eta theta iota kappa"
        val table = tableDraw("| Left | Right |\n| --- | --- |\n| a | $prose |\n", 200)
        assertEquals(2, table.cells.size)
        assertTrue(table.cells[0].width >= 40, "first column ${table.cells[0].width} px breaks 'Left'")
        assertTrue(table.cells[1].width >= 70, "second column ${table.cells[1].width} px breaks 'epsilon'")
        assertTrue(table.totalWidth <= 200, "squeezable table fits its column: ${table.totalWidth}")
    }

    @Test
    fun `a narrow first column is not shrunk below its word even when the proportional share is smaller`() {
        val prose = List(30) { "word$it" }.joinToString(" ")
        val table = tableDraw("| Identifier | Notes |\n| --- | --- |\n| x | $prose |\n", 300)
        assertTrue(table.cells[0].width >= 100, "Identifier needs 100 px, got ${table.cells[0].width}")
    }

    @Test
    fun `when the longest words alone exceed the column the table scrolls and no cell is narrower than its word`() {
        val table = tableDraw("| Alphabetical | Descriptions |\n| --- | --- |\n| a | b |\n", 200)
        assertTrue(table.totalWidth > table.available, "framed figure scrolls: ${table.totalWidth} vs ${table.available}")
        assertTrue(table.cells[0].width >= 120)
        assertTrue(table.cells[1].width >= 120)
    }
}
