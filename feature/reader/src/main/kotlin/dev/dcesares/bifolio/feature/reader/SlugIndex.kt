package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ListBlock
import java.net.URLDecoder

/**
 * Maps heading slugs to the index of the top-level block that contains them, so an internal link
 * `#slug` scrolls to a block index (SYN-01, NAV-08 internal part). Headings nested in quotes, lists
 * and details resolve to their top-level container.
 */
internal class SlugIndex(
    blocks: List<Block>,
) {
    private val byslug: Map<String, Int> =
        buildMap { forEachHeading(blocks) { index, heading -> putIfAbsent(heading.slug, index) } }

    /** Block index for a link destination such as `#my-heading` or `#caf%C3%A9`; null if not found. */
    fun blockIndexOf(destination: String): Int? {
        if (!destination.startsWith('#')) return null
        val raw = destination.substring(1)
        return byslug[raw] ?: byslug[decode(raw)] ?: byslug[decode(raw).lowercase()]
    }

    private fun decode(text: String): String =
        try {
            // A literal plus is not a space in a fragment.
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            text
        }
}

/**
 * Visits every heading in document order, including those nested in quotes, lists, details, callouts and
 * footnote definitions, with the index of the top-level block that holds it. The one place that knows which
 * containers can hold headings: the slug index and the table of contents both read it.
 */
internal fun forEachHeading(
    blocks: List<Block>,
    action: (topLevelIndex: Int, heading: Heading) -> Unit,
) {
    blocks.forEachIndexed { index, block -> visitHeadings(block) { action(index, it) } }
}

private fun visitHeadings(
    block: Block,
    action: (Heading) -> Unit,
) {
    when (block) {
        is Heading -> action(block)
        is BlockQuote -> block.blocks.forEach { visitHeadings(it, action) }
        is ListBlock -> block.items.forEach { item -> item.blocks.forEach { visitHeadings(it, action) } }
        is HtmlDetails -> block.blocks.forEach { visitHeadings(it, action) }
        is Callout -> block.blocks.forEach { visitHeadings(it, action) }
        is FootnoteDefinition -> block.blocks.forEach { visitHeadings(it, action) }
        else -> Unit
    }
}
