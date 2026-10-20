package dev.dcesares.bifolio.highlight

/** Markup scanner: comments, tag names, attribute names and quoted values. One forward pass. */
private const val COMMENT_OPEN = 4
private const val COMMENT_CLOSE = 3

internal class HtmlScanner(
    private val src: CharSequence,
    private val out: SpanSink,
) {
    private val n = src.length
    private var pos = 0

    fun run() {
        while (pos < n) {
            when {
                src.startsWithAt("<!--", pos) -> comment()
                src[pos] == '<' && pos + 1 < n && isTagStart(src[pos + 1]) -> tag()
                else -> text()
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

    private fun comment() {
        var j = pos + COMMENT_OPEN
        while (j < n && !src.startsWithAt("-->", j)) j++
        emitTo(minOf(n, j + COMMENT_CLOSE), TokenKind.COMMENT)
    }

    private fun text() {
        var j = pos + 1
        while (j < n && src[j] != '<') j++
        emitTo(j, TokenKind.PLAIN)
    }

    private fun tag() {
        var j = pos + 1
        while (j < n && isTagMark(src[j])) j++
        emitTo(j, TokenKind.PUNCTUATION)
        j = pos
        while (j < n && isNamePart(src[j])) j++
        emitTo(j, TokenKind.KEYWORD)
        while (pos < n && !isTagEnd(src[pos])) attributePart()
        if (pos < n && src[pos] == '>') emitTo(pos + 1, TokenKind.PUNCTUATION)
    }

    private fun attributePart() {
        val c = src[pos]
        when {
            c.isWhitespace() -> blanks()
            c == '"' || c == '\'' -> quoted(c)
            c == '=' -> emitTo(pos + 1, TokenKind.OPERATOR)
            c == '/' -> emitTo(pos + 1, TokenKind.PUNCTUATION)
            isNamePart(c) -> word()
            else -> emitTo(pos + 1, TokenKind.PLAIN)
        }
    }

    private fun blanks() {
        var j = pos
        while (j < n && src[j].isWhitespace()) j++
        emitTo(j, TokenKind.PLAIN)
    }

    private fun quoted(quote: Char) {
        var j = pos + 1
        while (j < n && src[j] != quote) j++
        emitTo(minOf(n, j + 1), TokenKind.STRING)
    }

    private fun word() {
        var j = pos
        while (j < n && isNamePart(src[j])) j++
        emitTo(j, if (j < n && src[j] == '=') TokenKind.TYPE else attributeValueOrFlag())
    }

    /** Bare words after `=` are values; bare flags (`disabled`) are attribute names. */
    private fun attributeValueOrFlag(): TokenKind {
        var k = pos - 1
        while (k >= 0 && src[k].isWhitespace()) k--
        return if (k >= 0 && src[k] == '=') TokenKind.STRING else TokenKind.TYPE
    }
}

private fun isTagMark(c: Char) = c == '/' || c == '!' || c == '?'

private fun isTagEnd(c: Char) = c == '>' || c == '<'

private fun isNamePart(c: Char) = c.isLetterOrDigit() || c == '-' || c == ':' || c == '_'

private fun isTagStart(c: Char) = c.isLetter() || c == '/' || c == '!' || c == '?'
