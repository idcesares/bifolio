package dev.dcesares.bifolio.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTest {
    private val r = SourceRange(0, 1)

    @Test
    fun `DOC-07 range is half open and validated`() {
        assertThrows(IllegalArgumentException::class.java) { SourceRange(-1, 2) }
        assertThrows(IllegalArgumentException::class.java) { SourceRange(3, 2) }
        val range = SourceRange(2, 5)
        assertEquals(3, range.length)
        assertTrue(2 in range)
        assertFalse(5 in range)
        assertTrue(SourceRange(1, 9).contains(range))
        assertFalse(range.contains(SourceRange(1, 9)))
    }

    @Test
    fun `SYN-01 heading level is 1 to 6`() {
        assertThrows(IllegalArgumentException::class.java) { Heading(0, emptyList(), "", r) }
        assertThrows(IllegalArgumentException::class.java) { Heading(7, emptyList(), "", r) }
        Heading(6, emptyList(), "x", r)
    }

    @Test
    fun `SYN-06 table rows align with column alignments`() {
        val cell = TableCell(listOf(Inline.Text("a")))
        val t = Table(listOf(ColumnAlignment.LEFT), TableRow(listOf(cell)), emptyList(), r)
        assertEquals(1, t.columnCount)
        assertThrows(IllegalArgumentException::class.java) {
            Table(listOf(ColumnAlignment.LEFT, ColumnAlignment.NONE), TableRow(listOf(cell)), emptyList(), r)
        }
    }

    @Test
    fun `DOC-08 nestingDepth counts lists and quotes`() {
        val leaf = Paragraph(listOf(Inline.Text("x")), r)
        val quote = BlockQuote(listOf(leaf), r)
        val list = ListBlock(false, 1, true, listOf(ListItem(null, listOf(quote), r)), r)
        assertEquals(0, nestingDepth(listOf(leaf)))
        assertEquals(2, nestingDepth(listOf(list)))
    }

    @Test
    fun `SYN-02 plainText flattens inlines`() {
        val inl =
            listOf(
                Inline.Text("a "),
                Inline.Strong(listOf(Inline.Text("b"))),
                Inline.SoftBreak,
                Inline.Code("c"),
                Inline.Link("u", null, listOf(Inline.Text("d"))),
                Inline.Image("s", null, "alt"),
            )
        assertEquals("a b\ncdalt", plainText(inl))
    }
}
