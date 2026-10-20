package dev.dcesares.bifolio.highlight

private const val OPERATOR_CHARS = "+-*/%=<>!&|^~?:"
private const val PUNCTUATION_CHARS = "(){}[];,.@\\#$"

/** Single left-to-right pass; every branch advances [pos], so total work is O(length). */
internal class GenericScanner(
    private val spec: LanguageSpec,
    private val src: CharSequence,
    private val out: SpanSink,
) {
    private val n = src.length
    private var pos = 0
    private val classifier = WordClassifier(spec, src)

    fun run() {
        while (pos < n) step()
    }

    private fun step() {
        val c = src[pos]
        when {
            c.isWhitespace() -> whitespace()
            spec.startsLineComment(src, pos) -> emitTo(src.lineEnd(pos), TokenKind.COMMENT)
            spec.blockOpen != null && src.startsWithAt(spec.blockOpen, pos) -> blockComment()
            spec.quotes.indexOf(c) >= 0 -> string(c)
            c.isDigit() -> number()
            spec.dollarVars && c == '$' -> dollar()
            spec.isIdentStart(c) -> identifier()
            else -> {
                val kind =
                    when {
                        OPERATOR_CHARS.indexOf(c) >= 0 -> TokenKind.OPERATOR
                        PUNCTUATION_CHARS.indexOf(c) >= 0 -> TokenKind.PUNCTUATION
                        else -> TokenKind.PLAIN
                    }
                emitTo(pos + 1, kind)
            }
        }
    }

    private fun emitTo(
        end: Int,
        kind: TokenKind,
    ) {
        out.add(pos, end, kind)
        pos = end
    }

    private fun whitespace() {
        var j = pos
        while (j < n && src[j].isWhitespace()) j++
        emitTo(j, TokenKind.PLAIN)
    }

    private fun blockComment() {
        val open = spec.blockOpen ?: return
        val close = spec.blockClose ?: return
        var j = pos + open.length
        while (j < n && !src.startsWithAt(close, j)) j++
        emitTo(minOf(n, j + close.length), TokenKind.COMMENT)
    }

    private fun string(quote: Char) {
        val end = stringEnd(src, spec, pos, quote)
        emitTo(end, if (classifier.isKeyAhead(end, strict = false)) TokenKind.TYPE else TokenKind.STRING)
    }

    private fun number() {
        var j = pos + 1
        while (j < n) {
            val ch = src[j]
            val part = ch.isLetterOrDigit() || ch == '_' || (ch == '.' && j + 1 < n && src[j + 1].isDigit())
            if (!part) break
            j++
        }
        emitTo(j, TokenKind.NUMBER)
    }

    private fun dollar() {
        var j = pos + 1
        while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
        if (j == pos + 1) emitTo(j, TokenKind.PUNCTUATION) else emitTo(j, TokenKind.TYPE)
    }

    private fun identifier() {
        var j = pos + 1
        while (j < n && spec.isIdentPart(src[j])) j++
        emitTo(j, classifier.classify(pos, j))
    }
}
