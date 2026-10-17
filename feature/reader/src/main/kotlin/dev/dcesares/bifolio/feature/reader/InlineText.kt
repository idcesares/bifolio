package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.InlineStyle

private const val CODE_SCALE = 0.92f
private const val SCRIPT_SCALE = 0.75f
private const val MARK_ALPHA = 0.25f

/**
 * Inline content to styled text (SYN-01..07, DOC-09). Links carry their destination; [onLink] receives it
 * when the reader taps. Soft breaks are spaces, hard breaks are newlines.
 * Images, footnote references and inline math are reserved for M5 and render as plain text here.
 */
internal fun buildInlineText(
    inlines: List<Inline>,
    theme: InlineTheme,
    onLink: (String) -> Unit,
): AnnotatedString = AnnotatedString.Builder().also { it.append(inlines, theme, onLink) }.toAnnotatedString()

private fun AnnotatedString.Builder.append(
    inlines: List<Inline>,
    theme: InlineTheme,
    onLink: (String) -> Unit,
) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Emphasis ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.children, theme, onLink) }
            is Inline.Strong ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.children, theme, onLink) }
            is Inline.Strikethrough ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(inline.children, theme, onLink)
                }
            is Inline.Code -> withStyle(codeStyle(theme)) { append(inline.text) }
            is Inline.Link -> {
                val styles =
                    TextLinkStyles(SpanStyle(color = theme.link, textDecoration = TextDecoration.Underline))
                val annotation =
                    LinkAnnotation.Clickable(
                        tag = inline.destination,
                        styles = styles,
                        linkInteractionListener = { onLink(inline.destination) },
                    )
                withLink(annotation) { append(inline.children, theme, onLink) }
            }
            is Inline.Styled -> withStyle(styleOf(inline.style, theme)) { append(inline.children, theme, onLink) }
            Inline.SoftBreak -> append(' ')
            Inline.HardBreak -> append('\n')
            is Inline.Image -> append(inline.alt)
            is Inline.FootnoteReference -> withStyle(styleOf(InlineStyle.SUPERSCRIPT, theme)) { append(inline.label) }
            is Inline.InlineMath -> withStyle(codeStyle(theme)) { append(inline.tex) }
        }
    }
}

private fun codeStyle(theme: InlineTheme) =
    SpanStyle(fontFamily = theme.codeFamily, fontSize = CODE_SCALE.em, background = theme.codeBackground)

private fun styleOf(
    style: InlineStyle,
    theme: InlineTheme,
): SpanStyle =
    when (style) {
        InlineStyle.SUBSCRIPT -> SpanStyle(fontSize = SCRIPT_SCALE.em, baselineShift = BaselineShift.Subscript)
        InlineStyle.SUPERSCRIPT -> SpanStyle(fontSize = SCRIPT_SCALE.em, baselineShift = BaselineShift.Superscript)
        InlineStyle.KEYBOARD -> codeStyle(theme)
        InlineStyle.MARK -> SpanStyle(background = theme.markBackground.copy(alpha = MARK_ALPHA))
        InlineStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    }
