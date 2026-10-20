package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.plainText

/** Headings up to this level are listed; deeper ones are collapsed under the last heading at or above it (UI-07). */
internal const val TOC_LAST_SHOWN_LEVEL = 3

/** One heading of the contents. [anchor] is where the heading starts, never a page (ANC-01). */
internal data class TocEntry(
    val level: Int,
    val title: String,
    val slug: String,
    val anchor: Anchor,
)

/** One line of the list. [deeper] counts the collapsed headings below it; [expanded] says whether they are shown. */
internal data class TocRow(
    val index: Int,
    val entry: TocEntry,
    val depth: Int,
    val deeper: Int,
    val expanded: Boolean,
)

/**
 * The table of contents of a document (UI-07): the headings in order, each as an anchor, the section the reader
 * is in, and the H1 to H3 / deeper split. Pure, so the JVM tests it. Headings come from [forEachHeading], the same
 * walk that resolves internal links, so nested headings are found the same way.
 */
internal class TableOfContents(
    blocks: List<Block>,
) {
    val entries: List<TocEntry> =
        buildList {
            forEachHeading(blocks) { top, heading ->
                val title = plainText(heading.content).trim()
                if (title.isNotEmpty()) {
                    val offset = (heading.range.start - blocks[top].range.start).coerceAtLeast(0)
                    add(TocEntry(heading.level, title, heading.slug, Anchor(top, offset)))
                }
            }
        }

    val isEmpty: Boolean get() = entries.isEmpty()

    /** For each entry, the entry it is listed under: itself, or the last shallow heading before a deep one. */
    private val owners: IntArray =
        IntArray(entries.size).also { owners ->
            var lastShallow = -1
            entries.forEachIndexed { index, entry ->
                if (entry.level <= TOC_LAST_SHOWN_LEVEL) {
                    lastShallow = index
                    owners[index] = index
                } else {
                    owners[index] = if (lastShallow >= 0) lastShallow else index
                }
            }
        }

    private val shallowestLevel: Int =
        entries.filter { it.level <= TOC_LAST_SHOWN_LEVEL }.minOfOrNull { it.level } ?: 1

    /** Index of the entry that stands for [index] in the list: the heading itself, or its shallow parent. */
    fun ownerOf(index: Int): Int = owners[index]

    /** The section [anchor] is in: the last heading that starts at or before it. Null before the first heading. */
    fun currentIndex(anchor: Anchor): Int? {
        var low = 0
        var high = entries.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (entries[mid].anchor.isAtOrBefore(anchor)) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found.takeIf { it >= 0 }
    }

    /** Lines to show: every shallow heading, and the deeper ones below each parent listed in [expanded]. */
    fun rows(expanded: Set<Int>): List<TocRow> {
        val deeperOf = IntArray(entries.size)
        entries.indices.forEach { if (owners[it] != it) deeperOf[owners[it]]++ }
        return buildList {
            entries.forEachIndexed { index, entry ->
                val owner = owners[index]
                if (owner == index || owner in expanded) {
                    val orphan = owner == index && entry.level > TOC_LAST_SHOWN_LEVEL
                    val depth = if (orphan) 0 else (entry.level - shallowestLevel).coerceAtLeast(0)
                    add(TocRow(index, entry, depth, deeperOf[index], index in expanded && deeperOf[index] > 0))
                }
            }
        }
    }

    /** The line to highlight for [current]: its own line when shown, else the parent it is collapsed under. */
    fun highlightedRow(
        current: Int?,
        expanded: Set<Int>,
    ): Int? {
        if (current == null) return null
        val owner = owners[current]
        return if (owner == current || owner in expanded) current else owner
    }

    /** Parents to open at first, so the current section is visible in the list. */
    fun initiallyExpanded(current: Int?): Set<Int> =
        if (current != null && owners[current] != current) setOf(owners[current]) else emptySet()
}

private fun Anchor.isAtOrBefore(other: Anchor): Boolean =
    blockIndex < other.blockIndex || (blockIndex == other.blockIndex && charOffset <= other.charOffset)
