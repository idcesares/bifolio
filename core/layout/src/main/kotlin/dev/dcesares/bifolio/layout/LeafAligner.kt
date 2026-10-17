package dev.dcesares.bifolio.layout

/**
 * Aligns the rendered text of one inline to a window of the source (ADR-0005).
 *
 * The window comes from the inline's source range, so it holds only that inline's characters plus the
 * escapes (`\*`) and entities (`&amp;`) the parser dropped or decoded. Rendered characters are matched in
 * order: an escape or entity maps to its first source character, any other character to the next equal one.
 * Without a range the window is the rest of the block and the match is greedy with a short lookahead.
 *
 * Results are non-decreasing and stay inside the window. Stateless and thread-safe.
 */
internal object LeafAligner {
    private const val LOOKAHEAD = 512

    /** Returns `text.length + 1` source offsets within `[from, to]`, non-decreasing (greedy fallback). */
    fun build(
        text: String,
        source: String,
        from: Int,
        to: Int,
    ): IntArray {
        val map = IntArray(text.length + 1)
        val end = align(text, source, from, to, LOOKAHEAD, map, 0, decode = true)
        map[text.length] = minOf(end, to)
        return map
    }

    /**
     * Writes the source offset of `text[i]` to `out[outAt + i]` and returns the source cursor after the
     * last character. [decode] enables escape and entity handling (off for code spans).
     */
    @Suppress("LongParameterList")
    fun align(
        text: String,
        source: String,
        from: Int,
        to: Int,
        lookahead: Int,
        out: IntArray,
        outAt: Int,
        decode: Boolean,
    ): Int {
        val n = text.length
        val last = maxOf(to - 1, from)
        var j = from
        var i = 0
        while (i < n) {
            val c = text[i]
            val limit = if (lookahead >= to - j) to else j + lookahead
            var m = j
            var kind = MISS
            while (m < limit) {
                kind = classify(source, m, to, text, i, c, decode)
                if (kind != MISS) break
                m++
            }
            if (kind == MISS) {
                out[outAt + i] = minOf(j, last)
                i++
                continue
            }
            when (kind) {
                EQUAL -> {
                    out[outAt + i] = m
                    j = m + 1
                    i++
                }
                ESCAPE -> {
                    out[outAt + i] = m
                    j = m + 2
                    i++
                }
                else -> {
                    val end = Entities.end(source, m, to)
                    val k = Entities.chars(source, m, end, to, text, i)
                    for (q in 0 until k) out[outAt + i + q] = m
                    j = end + 1
                    i += k
                }
            }
        }
        return j
    }

    private const val MISS = 0
    private const val EQUAL = 1
    private const val ESCAPE = 2
    private const val ENTITY = 3

    private fun classify(
        source: String,
        m: Int,
        to: Int,
        text: String,
        i: Int,
        c: Char,
        decode: Boolean,
    ): Int {
        val s = source[m]
        if (decode) {
            if (s == '\\' && isEscapedAt(source, m, to, c)) return ESCAPE
            if (s == '&' && isEntityAt(source, m, to, text, i)) return ENTITY
        }
        return if (s == c || (s == '\n' && c == ' ')) EQUAL else MISS
    }

    /** A reference that was decoded, not one the text still shows literally (as in `&foo;`). */
    private fun isEntityAt(
        source: String,
        m: Int,
        to: Int,
        text: String,
        i: Int,
    ): Boolean {
        val end = Entities.end(source, m, to)
        return end >= 0 && !text.regionMatches(i, source, m, end - m + 1)
    }

    /** A backslash at [m] that escapes the ASCII punctuation character [c] following it. */
    private fun isEscapedAt(
        source: String,
        m: Int,
        to: Int,
        c: Char,
    ): Boolean = m + 1 < to && source[m + 1] == c && isAsciiPunctuation(c)

    private fun isAsciiPunctuation(c: Char): Boolean = c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'
}
