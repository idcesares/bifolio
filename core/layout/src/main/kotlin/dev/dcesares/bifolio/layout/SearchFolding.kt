package dev.dcesares.bifolio.layout

import java.text.Normalizer

/** Folded text plus, for each folded char, the half open window `[origStart, origEnd)` of the original it came from. */
internal class Folded(
    val text: String,
    val origStart: IntArray,
    val origEnd: IntArray,
)

/**
 * Search normal form: canonical decomposition with combining marks dropped, lower case, every whitespace or
 * space separator a plain space. A dropped mark extends the window of the char before it, so a match on
 * `e` also covers a decomposed `e` + acute.
 */
internal object SearchFolding {
    private const val ASCII = 128

    fun fold(s: String): Folded {
        val sb = StringBuilder(s.length)
        var starts = IntArray(s.length + 1)
        var ends = IntArray(s.length + 1)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val next = i + Character.charCount(cp)
            val before = sb.length
            if (cp < ASCII) {
                sb.append(if (cp.toChar().isWhitespace()) ' ' else cp.toChar().lowercaseChar())
            } else if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                sb.append(' ')
            } else {
                appendDecomposed(sb, cp)
            }
            val added = sb.length - before
            if (added == 0 && before > 0) ends[before - 1] = next
            if (sb.length > starts.size) {
                starts = starts.copyOf(sb.length * 2)
                ends = ends.copyOf(sb.length * 2)
            }
            for (k in before until sb.length) {
                starts[k] = i
                ends[k] = next
            }
            i = next
        }
        return Folded(sb.toString(), starts, ends)
    }

    private fun appendDecomposed(
        sb: StringBuilder,
        cp: Int,
    ) {
        val decomposed = Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFD)
        var j = 0
        while (j < decomposed.length) {
            val c = decomposed.codePointAt(j)
            j += Character.charCount(c)
            if (!isMark(c)) sb.appendCodePoint(Character.toLowerCase(c))
        }
    }

    private fun isMark(cp: Int): Boolean =
        when (Character.getType(cp).toByte()) {
            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
            else -> false
        }
}

/** Context windows around a match (UI-08). */
internal object SearchSnippets {
    private const val CONTEXT = 40
    private const val ELLIPSIS = '…'

    fun of(
        text: String,
        start: Int,
        end: Int,
    ): SearchSnippet {
        val from = windowStart(text, start)
        val to = windowEnd(text, end)
        val sb = StringBuilder(to - from + 2)
        if (from > 0) sb.append(ELLIPSIS)
        val lead = sb.length
        for (k in from until to) sb.append(if (text[k].isWhitespace()) ' ' else text[k])
        if (to < text.length) sb.append(ELLIPSIS)
        return SearchSnippet(sb.toString(), lead + (start - from), lead + (end - from))
    }

    /** Starts after the first space when a word is cut by the window; otherwise keeps the hard cut. */
    private fun windowStart(
        text: String,
        start: Int,
    ): Int {
        var from = maxOf(start - CONTEXT, 0)
        if (from == 0) return 0
        var k = from
        while (k < start && !text[k].isWhitespace()) k++
        if (k < start) from = k + 1
        if (from < text.length && Character.isLowSurrogate(text[from])) from++
        return from
    }

    private fun windowEnd(
        text: String,
        end: Int,
    ): Int {
        var to = minOf(end + CONTEXT, text.length)
        if (to == text.length) return to
        var k = to
        while (k > end && !text[k].isWhitespace()) k--
        if (k > end) to = k
        if (to > 0 && Character.isHighSurrogate(text[to - 1])) to--
        return to
    }
}
