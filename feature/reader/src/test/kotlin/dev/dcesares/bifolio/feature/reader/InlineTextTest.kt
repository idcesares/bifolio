package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.dcesares.bifolio.model.Inline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SYN-02 (emphasis), SYN-04 (links) at the text level, DOC-09 (inline HTML subset). */
class InlineTextTest {
    private val theme = InlineTheme(Color.Blue, Color.LightGray, Color.Yellow, FontFamily.Monospace)

    private fun build(
        vararg inlines: Inline,
        onLink: (String) -> Unit = {},
    ) = buildInlineText(inlines.toList(), theme, onLink)

    @Test
    fun plainTextConcatenatesAndBreaksBecomeSpaceAndNewline() {
        val text = build(Inline.Text("a"), Inline.SoftBreak, Inline.Text("b"), Inline.HardBreak, Inline.Text("c"))
        assertEquals("a b\nc", text.text)
    }

    @Test
    fun emphasisAndStrongCoverTheirRange() {
        val text =
            build(
                Inline.Text("x "),
                Inline.Emphasis(listOf(Inline.Text("it"))),
                Inline.Strong(listOf(Inline.Text("b"))),
            )
        val italic = text.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertEquals(2 to 4, italic.start to italic.end)
        val bold = text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(4 to 5, bold.start to bold.end)
    }

    @Test
    fun linkCarriesDestinationAndFiresCallback() {
        val tapped = mutableListOf<String>()
        val text = build(Inline.Link("#intro", null, listOf(Inline.Text("go"))), onLink = { tapped += it })
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
        assertEquals("#intro", link.tag)
        link.linkInteractionListener?.onClick(link)
        assertEquals(listOf("#intro"), tapped)
    }

    @Test
    fun inlineCodeUsesTheCodeFamily() {
        val text = build(Inline.Code("val x"))
        assertTrue(text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertEquals("val x", text.text)
    }
}
