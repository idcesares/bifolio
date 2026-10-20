package dev.dcesares.bifolio.highlight

/** Decides keyword, type, function or key for an identifier-like word. O(word length). */
internal class WordClassifier(
    private val spec: LanguageSpec,
    private val src: CharSequence,
) {
    private val n = src.length

    fun classify(
        start: Int,
        end: Int,
    ): TokenKind {
        if (end - start <= spec.maxWord) {
            val raw = src.subSequence(start, end).toString()
            val word = if (spec.caseInsensitive) raw.lowercase() else raw
            if (word in spec.keywords) return TokenKind.KEYWORD
            if (word in spec.types) return TokenKind.TYPE
        }
        return when {
            end < n && src[end] == '(' -> TokenKind.FUNCTION
            isKeyAhead(end, strict = true) -> TokenKind.TYPE
            spec.capitalTypes && src[start].isUpperCase() -> TokenKind.TYPE
            else -> TokenKind.PLAIN
        }
    }

    /** Key detection: [end] is followed (same line, after blanks) by the key marker. */
    fun isKeyAhead(
        end: Int,
        strict: Boolean,
    ): Boolean {
        val marker = spec.keyMarker ?: return false
        var j = end
        while (j < n && (src[j] == ' ' || src[j] == '\t')) j++
        if (j >= n || src[j] != marker) return false
        if (!strict || marker != ':') return true
        return j + 1 >= n || src[j + 1].isWhitespace()
    }
}
