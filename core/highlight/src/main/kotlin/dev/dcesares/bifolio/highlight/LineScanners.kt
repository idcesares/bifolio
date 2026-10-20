package dev.dcesares.bifolio.highlight

private val DIFF_HEADERS = listOf("+++", "---", "diff ", "index ")

/** Unified-diff scanner: one kind per line (newline included). */
internal fun scanDiff(
    src: CharSequence,
    out: SpanSink,
) {
    var start = 0
    while (start < src.length) {
        val end = minOf(src.length, src.lineEnd(start) + 1)
        val kind =
            when {
                DIFF_HEADERS.any { src.startsWithAt(it, start) } -> TokenKind.COMMENT
                src.startsWithAt("@@", start) -> TokenKind.TYPE
                src[start] == '+' -> TokenKind.STRING
                src[start] == '-' -> TokenKind.KEYWORD
                else -> TokenKind.PLAIN
            }
        out.add(start, end, kind)
        start = end
    }
}

/** Markdown-as-code scanner: headings, quotes, fences, list markers and inline code spans. */
internal class MarkdownScanner(
    private val src: CharSequence,
    private val out: SpanSink,
) {
    private var inFence = false
    private var noMoreTicks = false

    fun run() {
        var start = 0
        while (start < src.length) {
            val eol = src.lineEnd(start)
            val end = minOf(src.length, eol + 1)
            line(start, end, eol)
            start = end
        }
    }

    private fun line(
        start: Int,
        end: Int,
        eol: Int,
    ) {
        var i = start
        while (i < eol && (src[i] == ' ' || src[i] == '\t')) i++
        noMoreTicks = false
        val fence = src.startsWithAt("```", i) || src.startsWithAt("~~~", i)
        when {
            fence -> {
                inFence = !inFence
                out.add(start, end, TokenKind.COMMENT)
            }
            inFence -> out.add(start, end, TokenKind.STRING)
            isHeading(i, eol) -> out.add(start, end, TokenKind.KEYWORD)
            i < eol && src[i] == '>' -> out.add(start, end, TokenKind.COMMENT)
            else -> {
                out.add(start, i, TokenKind.PLAIN)
                val after = marker(i, eol)
                out.add(i, after, TokenKind.OPERATOR)
                inline(after, end, eol)
            }
        }
    }

    private fun isHeading(
        from: Int,
        eol: Int,
    ): Boolean {
        if (from >= eol || src[from] != '#') return false
        var j = from
        while (j < eol && src[j] == '#') j++
        return j - from <= MAX_HEADING && (j == eol || src[j] == ' ')
    }

    private fun marker(
        from: Int,
        eol: Int,
    ): Int {
        var j = from
        if (j < eol && isBullet(src[j])) {
            j++
        } else {
            while (j < eol && src[j].isDigit()) j++
            if (j == from || j >= eol || src[j] != '.') return from
            j++
        }
        return if (j < eol && src[j] == ' ') j else from
    }

    private fun inline(
        from: Int,
        end: Int,
        eol: Int,
    ) {
        var i = from
        var plainStart = from
        while (i < eol) {
            val close = if (src[i] == '`' && !noMoreTicks) closingTick(i + 1, eol) else -1
            if (close < 0) {
                i++
            } else {
                out.add(plainStart, i, TokenKind.PLAIN)
                out.add(i, close + 1, TokenKind.STRING)
                i = close + 1
                plainStart = i
            }
        }
        out.add(plainStart, end, TokenKind.PLAIN)
    }

    /** Index of the next backtick before [eol], or -1 (and stop searching on this line). */
    private fun closingTick(
        from: Int,
        eol: Int,
    ): Int {
        var j = from
        while (j < eol && src[j] != '`') j++
        if (j >= eol) {
            noMoreTicks = true
            return -1
        }
        return j
    }

    private companion object {
        const val MAX_HEADING = 6
    }
}

private fun isBullet(c: Char) = c == '-' || c == '*' || c == '+'
