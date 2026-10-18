package dev.dcesares.bifolio.model

/** Words left to read, in the whole document and in the current section (UI-06). */
public data class WordsRemaining(
    val inDocument: Int,
    val inSection: Int,
)

/**
 * Word counts by position in [Document.source]. A word is a run of letters or digits, so Markdown marks do not
 * count and the figure is independent of geometry (ADR-0005). Built once per document.
 */
public class WordIndex(
    private val document: Document,
) {
    private val starts: IntArray = wordStarts(document.source)

    /** Words left from [anchor] to the end of the document and to the end of its section. */
    public fun remaining(anchor: Anchor): WordsRemaining {
        val from = document.sourceOffsetOf(anchor)
        return WordsRemaining(
            inDocument = wordsBetween(from, document.source.length),
            inSection = wordsBetween(from, sectionEnd(anchor)),
        )
    }

    /** Words whose first character lies in `[from, to)`. */
    public fun wordsBetween(
        from: Int,
        to: Int,
    ): Int = (firstAtOrAfter(to) - firstAtOrAfter(from)).coerceAtLeast(0)

    /** The section is the last heading at or before the anchor up to the next heading of the same or higher rank. */
    private fun sectionEnd(anchor: Anchor): Int {
        val blocks = document.blocks
        if (blocks.isEmpty()) return 0
        val at = anchor.blockIndex.coerceIn(0, blocks.lastIndex)
        val level =
            (at downTo 0).firstNotNullOfOrNull { (blocks[it] as? Heading)?.level } ?: return document.source.length
        val next =
            (at + 1..blocks.lastIndex).firstOrNull {
                (blocks[it] as? Heading)?.let { h -> h.level <= level } ==
                    true
            }
        return next?.let { blocks[it].range.start } ?: document.source.length
    }

    private fun firstAtOrAfter(offset: Int): Int {
        var low = 0
        var high = starts.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (starts[mid] < offset) low = mid + 1 else high = mid
        }
        return low
    }

    private companion object {
        fun wordStarts(source: String): IntArray {
            val found = ArrayList<Int>()
            var inWord = false
            source.forEachIndexed { index, c ->
                val letter = c.isLetterOrDigit()
                if (letter && !inWord) found += index
                inWord = letter
            }
            return found.toIntArray()
        }
    }
}
