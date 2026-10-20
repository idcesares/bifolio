package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ListBlock

/** A footnote definition and the index of the top-level block that holds it, which is where a jump lands. */
internal class NoteDefinition(
    val block: FootnoteDefinition,
    val blockIndex: Int,
)

/**
 * Footnote definitions by label (SYN-09, UI-09). A label defined twice resolves to the first definition in source
 * order. A label that is not defined exactly is looked up again ignoring case, so `[^Note]` finds `[^note]:`.
 * Definitions nested in containers resolve to their top-level block.
 */
internal class NoteIndex(
    blocks: List<Block>,
) {
    private val exact = LinkedHashMap<String, NoteDefinition>()
    private val folded = HashMap<String, NoteDefinition>()

    init {
        blocks.forEachIndexed { index, block -> collect(block, index) }
    }

    private fun collect(
        block: Block,
        top: Int,
    ) {
        when (block) {
            is FootnoteDefinition -> {
                val definition = NoteDefinition(block, top)
                exact.putIfAbsent(block.label, definition)
                folded.putIfAbsent(block.label.lowercase(), definition)
            }
            is BlockQuote -> block.blocks.forEach { collect(it, top) }
            is ListBlock -> block.items.forEach { item -> item.blocks.forEach { collect(it, top) } }
            is HtmlDetails -> block.blocks.forEach { collect(it, top) }
            is Callout -> block.blocks.forEach { collect(it, top) }
            else -> Unit
        }
    }

    fun definitionOf(label: String): NoteDefinition? {
        if (label.isEmpty()) return null
        return exact[label] ?: folded[label.lowercase()]
    }
}
