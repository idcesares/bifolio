package dev.dcesares.bifolio.layout

/**
 * Interim mapping from rendered text offsets to source offsets (ADR-0005 open point).
 *
 * Anchors count source characters, but the measurer sees rendered text, which lacks the Markdown markup.
 * Without inline source spans, each rendered character is matched in order, greedily, to the next equal
 * source character. The result is monotone and exact for text without markup between its characters.
 */
internal object OffsetMap {
    private const val LOOKAHEAD = 512

    /** Returns `text.length + 1` source offsets within `[from, to]`, non-decreasing. */
    fun build(
        text: String,
        source: String,
        from: Int,
        to: Int,
    ): IntArray {
        val map = IntArray(text.length + 1)
        val last = maxOf(to - 1, from)
        var j = from
        for (i in text.indices) {
            val c = text[i]
            val limit = minOf(to, j + LOOKAHEAD)
            var k = j
            while (k < limit && source[k] != c) k++
            if (k < limit) {
                map[i] = k
                j = k + 1
            } else {
                map[i] = minOf(j, last)
            }
        }
        map[text.length] = minOf(j, to)
        return map
    }
}
