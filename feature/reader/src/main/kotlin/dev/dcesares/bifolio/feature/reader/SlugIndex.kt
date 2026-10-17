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
        buildMap {
            blocks.forEachIndexed { index, block -> collect(block) { slug -> putIfAbsent(slug, index) } }
        }

    /** Block index for a link destination such as `#my-heading` or `#caf%C3%A9`; null if not found. */
    fun blockIndexOf(destination: String): Int? {
        if (!destination.startsWith('#')) return null
        val raw = destination.substring(1)
        return byslug[raw] ?: byslug[decode(raw)] ?: byslug[decode(raw).lowercase()]
    }

    private fun collect(
        block: Block,
        add: (String) -> Unit,
    ) {
        when (block) {
            is Heading -> add(block.slug)
            is BlockQuote -> block.blocks.forEach { collect(it, add) }
            is ListBlock -> block.items.forEach { item -> item.blocks.forEach { collect(it, add) } }
            is HtmlDetails -> block.blocks.forEach { collect(it, add) }
            is Callout -> block.blocks.forEach { collect(it, add) }
            is FootnoteDefinition -> block.blocks.forEach { collect(it, add) }
            else -> Unit
        }
    }

    private fun decode(text: String): String =
        try {
            // A literal plus is not a space in a fragment.
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            text
        }
}
