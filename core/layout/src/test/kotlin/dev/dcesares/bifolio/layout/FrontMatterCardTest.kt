package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SYN-13 and PAG-07: the card shows the title and the flat entries, and its height comes from its text. */
class FrontMatterCardTest {
    private fun card(yaml: String): FrontMatter =
        MarkdownParser
            .parse("---\n$yaml---\n\n# Body\n")
            .blocks
            .filterIsInstance<FrontMatter>()
            .single()

    @Test
    fun `SYN-13 rows list every flat entry except title and lang`() {
        val fm = card("title: Book\nlang: pt-BR\nauthor: Ana\ndate: 2026-10-22\n")
        assertEquals("Book", FrontMatterCard.title(fm))
        assertEquals("author: Ana\ndate: 2026-10-22", FrontMatterCard.rows(fm))
    }

    @Test
    fun `SYN-13 a card with only title and lang falls back to showing the language row`() {
        val fm = card("title: Book\nlang: pt-BR\n")
        assertEquals("Book", FrontMatterCard.title(fm))
        assertEquals("lang: pt-BR", FrontMatterCard.rows(fm))
    }

    @Test
    fun `SYN-13 a list shows as a comma row`() {
        val fm = card("tags:\n  - a\n  - b\n")
        assertNull(FrontMatterCard.title(fm))
        assertEquals("tags: a, b", FrontMatterCard.rows(fm))
    }

    @Test
    fun `SYN-13 a block without key value entries shows its source lines`() {
        val fm = card("just some text\n# a comment\n")
        assertEquals("just some text\n# a comment", FrontMatterCard.rows(fm))
    }

    @Test
    fun `SYN-13 a title only card has no rows`() {
        assertEquals("", FrontMatterCard.rows(card("title: Book\n")))
    }

    @Test
    fun `SYN-13 rows are capped and long values are cut with an ellipsis`() {
        val many = (1..12).joinToString("") { "k$it: v$it\n" }
        val rows = FrontMatterCard.rows(card(many)).lines()
        assertEquals(FrontMatterCard.MAX_ROWS, rows.size)
        assertEquals("k1: v1", rows.first())
        val long = FrontMatterCard.rows(card("note: ${"x".repeat(500)}\n"))
        assertTrue(long.length <= "note: ".length + FrontMatterCard.MAX_VALUE_CHARS)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `PAG-07 the card height is padding, title, gap and rows`() {
        val doc = MarkdownParser.parse("---\ntitle: Book\nauthor: Ana\ndate: 2026\n---\n\n# Body\n")
        val metrics = TEST_METRICS.copy(tableCellPadding = 10)
        val flattener = BlockFlattener(doc, FakeBlockMeasurer(), testGeometry(metrics = metrics))
        val layout = flattener.layout(0, 200)
        assertEquals(1, layout.lineCount)
        assertEquals(LineKind.ATOMIC, layout.draw.kind[0])
        // 10 + 20 (title) + 10 (gap, equal to the padding) + 40 (two rows) + 10
        assertEquals(10 + 20 + 10 + 40 + 10, layout.height(0, 1))
        assertEquals(200, layout.draw.width[0])
    }

    @Test
    fun `PAG-07 a long value wraps inside the padded width and grows the card`() {
        val doc = MarkdownParser.parse("---\nnote: ${"word ".repeat(20)}\n---\n\n# Body\n")
        val metrics = TEST_METRICS.copy(tableCellPadding = 10)
        val short = BlockFlattener(doc, FakeBlockMeasurer(), testGeometry(metrics = metrics)).layout(0, 200)
        // 180 px inner width at 10 px per char is 18 chars per line, so the row takes several lines.
        assertTrue(short.height(0, 1) > 10 + 20 + 10)
    }
}
