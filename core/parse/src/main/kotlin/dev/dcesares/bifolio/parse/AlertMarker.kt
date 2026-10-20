package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.CalloutKind
import org.commonmark.node.HardLineBreak
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.node.BlockQuote as CmBlockQuote
import org.commonmark.node.Paragraph as CmParagraph

/** Detects and strips the `[!KIND]` marker line of a GitHub alert quote (SYN-10). */
internal object AlertMarker {
    private val pattern = Regex("""\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]""", RegexOption.IGNORE_CASE)

    /** Alert kind when the quote's first line is only `[!KIND]`, else null. */
    fun kind(quote: CmBlockQuote): CalloutKind? {
        val first = quote.firstChild as? CmParagraph ?: return null
        val marker = StringBuilder()
        var node = first.firstChild
        while (node is Text) {
            marker.append(node.literal)
            node = node.next
        }
        if (node != null && node !is SoftLineBreak && node !is HardLineBreak) return null
        val m = pattern.matchEntire(marker.toString().trim()) ?: return null
        return CalloutKind.valueOf(m.groupValues[1].uppercase())
    }

    /** Removes the marker line (text nodes up to and including the first line break). */
    fun strip(quote: CmBlockQuote) {
        val paragraph = quote.firstChild as CmParagraph
        while (paragraph.firstChild is Text) paragraph.firstChild.unlink()
        paragraph.firstChild?.unlink()
        if (paragraph.firstChild == null) paragraph.unlink()
    }
}
