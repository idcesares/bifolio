package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.MermaidFence
import dev.dcesares.bifolio.model.Paragraph

/** A hit inside one text unit: `text[start until end]` in rendered characters. [focused] marks the current result. */
public data class HighlightRange(
    val start: Int,
    val end: Int,
    val focused: Boolean,
)

/**
 * Finds the occurrences of a search query in the text a surface draws and says which one is the current result
 * (UI-08). Occurrences are found in the drawn text itself, with the same folding and stepping as [DocumentSearch],
 * so nothing depends on pagination or on how a surface lays the text out.
 *
 * The current result is paired by source extent: a text unit that knows the source offsets of its first and last
 * rendered character owns the matches whose anchor falls inside that extent, and its k-th occurrence is the k-th of
 * those matches. When the counts differ (the result list was truncated, or the unit is not what the search read)
 * the unit highlights its hits and focuses none, so a wrong hit is never shown as current.
 *
 * Immutable and cheap to build; a new one is made per query, result list or current index. Thread safe.
 */
public class SearchHighlighter(
    private val document: Document,
    query: String,
    result: SearchResult,
    /** Index into [SearchResult.matches] of the current result, or any other value for none. */
    private val focusedIndex: Int,
) {
    private val needle: String = SearchFolding.fold(query.trim()).text

    /** Absolute source offset of each match, ascending, with the index of the match in the result. */
    private val offsets: IntArray
    private val order: IntArray

    init {
        val abs =
            IntArray(result.matches.size) { i ->
                val match = result.matches[i]
                val base =
                    document.blocks
                        .getOrNull(match.blockIndex)
                        ?.range
                        ?.start ?: 0
                base + match.anchor.charOffset
            }
        val sorted = abs.indices.sortedWith(compareBy({ abs[it] }, { it }))
        order = IntArray(sorted.size) { sorted[it] }
        offsets = IntArray(sorted.size) { abs[sorted[it]] }
    }

    /** False for a blank query: nothing is highlighted. */
    public val isActive: Boolean get() = needle.isNotEmpty()

    /**
     * Hits in the text of [block] when it is a heading, a paragraph, a code or mermaid block, or the summary of a
     * details block; any other block has none.
     */
    public fun hitsIn(block: Block): List<HighlightRange> {
        if (!isActive) return emptyList()
        val source = document.source
        return when (block) {
            is Heading -> rendered(RenderedText.of(block.content, source, block.range.start, block.range.end))
            is Paragraph -> rendered(RenderedText.of(block.content, source, block.range.start, block.range.end))
            is HtmlDetails -> rendered(RenderedText.of(block.summary, source, block.range.start, block.range.end))
            is CodeBlock -> code(block.code, block, block.fenced)
            is MermaidFence -> code(block.source, block, true)
            else -> emptyList()
        }
    }

    private fun rendered(text: Rendered): List<HighlightRange> =
        hitsIn(text.text, text.map[0], text.map[text.text.length])

    private fun code(
        code: String,
        block: Block,
        fenced: Boolean,
    ): List<HighlightRange> {
        val source = document.source
        val nl = source.indexOf('\n', block.range.start)
        val from = if (fenced && nl in 0 until block.range.end) nl + 1 else block.range.start
        val map = CodeSourceMap.build(code, source, from, block.range.end)
        return hitsIn(code, map[0], map[code.length])
    }

    /**
     * Hits in [text], a text unit as drawn. [sourceStart] and [sourceEnd] are the source offsets of its first
     * character and of where it ends (half open), or negative when the unit has no source mapping (a table cell):
     * such a unit is highlighted but never focused.
     */
    public fun hitsIn(
        text: String,
        sourceStart: Int,
        sourceEnd: Int,
    ): List<HighlightRange> {
        if (!isActive || text.length < needle.length) return emptyList()
        val folded = SearchFolding.fold(text)
        val found = ArrayList<IntArray>(1)
        var at = folded.text.indexOf(needle)
        while (at >= 0) {
            found.add(intArrayOf(folded.origStart[at], folded.origEnd[at + needle.length - 1]))
            at = folded.text.indexOf(needle, at + needle.length)
        }
        if (found.isEmpty()) return emptyList()
        var focused = -1
        if (sourceStart >= 0 && sourceEnd >= sourceStart) {
            val lo = lowerBound(sourceStart)
            val hi = lowerBound(sourceEnd)
            val owned = hi - lo
            if (owned == found.size) {
                val position = order.indexOf(focusedIndex, lo, hi)
                if (position >= 0) focused = position - lo
            }
        }
        return found.mapIndexed { i, range -> HighlightRange(range[0], range[1], i == focused) }
    }

    /** First position in [offsets] that is at least [value]. */
    private fun lowerBound(value: Int): Int {
        var lo = 0
        var hi = offsets.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (offsets[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun IntArray.indexOf(
        value: Int,
        from: Int,
        to: Int,
    ): Int {
        for (i in from until to) if (this[i] == value) return i
        return -1
    }
}
