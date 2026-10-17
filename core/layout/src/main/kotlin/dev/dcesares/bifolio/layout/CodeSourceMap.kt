package dev.dcesares.bifolio.layout

/**
 * Source offsets of the characters of a code block (ADR-0005). The code literal is the source lines without
 * their container prefix (`> `, list indent, code indent), so each code line is the tail of one source line.
 */
internal object CodeSourceMap {
    /** `code.length + 1` non-decreasing offsets within `[from, to]`; [from] is the first source line of the code. */
    fun build(
        code: String,
        source: String,
        from: Int,
        to: Int,
    ): IntArray {
        val map = IntArray(code.length + 1)
        var lineStart = minOf(from, to)
        var i = 0
        while (i <= code.length) {
            var nl = code.indexOf('\n', i)
            if (nl < 0) nl = code.length
            val len = nl - i
            var srcEnd = source.indexOf('\n', lineStart)
            if (srcEnd < 0 || srcEnd > to) srcEnd = to
            srcEnd = maxOf(srcEnd, lineStart)
            val offset =
                if (srcEnd - lineStart >= len &&
                    source.regionMatches(srcEnd - len, code, i, len)
                ) {
                    srcEnd - len
                } else {
                    lineStart
                }
            for (q in 0 until len) map[i + q] = minOf(offset + q, maxOf(to - 1, lineStart))
            map[nl] = if (nl == code.length) maxOf(srcEnd, offset + len).coerceAtMost(to) else srcEnd
            lineStart = minOf(srcEnd + 1, to)
            i = nl + 1
        }
        return map
    }
}
