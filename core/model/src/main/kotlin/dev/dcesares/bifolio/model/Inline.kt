package dev.dcesares.bifolio.model

/** Styles carried by the HTML subset allowed by DOC-09. */
public enum class InlineStyle { SUBSCRIPT, SUPERSCRIPT, KEYBOARD, MARK, UNDERLINE }

/** Inline content of text blocks. Immutable. */
public sealed interface Inline {
    public data class Text(
        val text: String,
    ) : Inline

    public data class Emphasis(
        val children: List<Inline>,
    ) : Inline

    public data class Strong(
        val children: List<Inline>,
    ) : Inline

    public data class Strikethrough(
        val children: List<Inline>,
    ) : Inline

    public data class Code(
        val text: String,
    ) : Inline

    public data class Link(
        val destination: String,
        val title: String?,
        val children: List<Inline>,
    ) : Inline

    /** Styling from `<sub>`, `<sup>`, `<kbd>`, `<mark>`, `<u>` (DOC-09). */
    public data class Styled(
        val style: InlineStyle,
        val children: List<Inline>,
    ) : Inline

    public data object SoftBreak : Inline

    public data object HardBreak : Inline

    /** Reserved for M5 (SYN-08). */
    public data class Image(
        val source: String,
        val title: String?,
        val alt: String,
    ) : Inline

    /** Reserved for M5 (SYN-09). */
    public data class FootnoteReference(
        val label: String,
    ) : Inline

    /** Reserved for M5 (SYN-12). */
    public data class InlineMath(
        val tex: String,
    ) : Inline
}

/** Concatenated visible text of [inlines]; soft and hard breaks become a newline. */
public fun plainText(inlines: List<Inline>): String {
    val sb = StringBuilder()
    appendPlainText(inlines, sb)
    return sb.toString()
}

private fun appendPlainText(
    inlines: List<Inline>,
    sb: StringBuilder,
) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> sb.append(inline.text)
            is Inline.Code -> sb.append(inline.text)
            is Inline.Emphasis -> appendPlainText(inline.children, sb)
            is Inline.Strong -> appendPlainText(inline.children, sb)
            is Inline.Strikethrough -> appendPlainText(inline.children, sb)
            is Inline.Link -> appendPlainText(inline.children, sb)
            is Inline.Styled -> appendPlainText(inline.children, sb)
            Inline.SoftBreak, Inline.HardBreak -> sb.append('\n')
            is Inline.Image -> sb.append(inline.alt)
            is Inline.FootnoteReference -> Unit
            is Inline.InlineMath -> sb.append(inline.tex)
        }
    }
}
