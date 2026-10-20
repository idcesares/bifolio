package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import dev.dcesares.bifolio.layout.DocumentSearch
import dev.dcesares.bifolio.layout.SearchHighlighter
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** UI-08: a hit found in rendered characters lands on the same word in the scroll text, which draws labels. */
class FootnoteShiftTest {
    @Test
    fun `labels before a hit move it right and a label right after it stays out`() {
        val inlines =
            listOf(
                Inline.Text("one"),
                Inline.FootnoteReference("12"),
                Inline.Text(" two"),
                Inline.FootnoteReference("3"),
            )
        val shift = FootnoteShift(inlines)
        // Rendered "one two": the label "12" is drawn between "one" and " two".
        assertEquals(0, shift.start(0))
        assertEquals(3, shift.end(3))
        assertEquals(5, shift.start(3))
        assertEquals(9, shift.end(7))
        assertEquals(6, shift.start(4))
    }

    @Test
    fun `no footnotes means no shift`() {
        val shift = FootnoteShift(listOf(Inline.Text("plain"), Inline.Strong(listOf(Inline.Text("bold")))))
        assertEquals(4, shift.start(4))
        assertEquals(9, shift.end(9))
    }

    @Test
    fun `hits in a parsed paragraph with footnote labels cover the same word in the drawn text`() {
        val text = "Alpha beta[^1] gamma beta[^2] delta.\n\n[^1]: one\n[^2]: two\n"
        val document = MarkdownParser.parse(text)
        val paragraph = document.blocks.filterIsInstance<Paragraph>().first()
        val drawn =
            buildInlineText(
                paragraph.content,
                InlineTheme(Color.Blue, Color.Gray, Color.Red, FontFamily.Monospace),
            ) {}.text
        val highlighter =
            SearchHighlighter(document, "beta", DocumentSearch.search(document, "beta"), focusedIndex = 0)
        val shift = FootnoteShift(paragraph.content)
        val hits = highlighter.hitsIn(paragraph)
        assertEquals(2, hits.size)
        for (hit in hits) {
            assertEquals("beta", drawn.substring(shift.start(hit.start), shift.end(hit.end)))
        }
    }
}
