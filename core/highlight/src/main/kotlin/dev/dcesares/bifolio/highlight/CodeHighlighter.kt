package dev.dcesares.bifolio.highlight

/** Token classes (ADR-0007). Colors are mapped per theme in core:designsystem. */
public enum class TokenKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    TYPE,
    FUNCTION,
    OPERATOR,
    PUNCTUATION,
    PLAIN,
}

/** Half-open range `[start, end)` of the source classified as [kind]. */
public data class TokenSpan(
    val start: Int,
    val end: Int,
    val kind: TokenKind,
)

/** Splits code into typed spans (SYN-11). Safe to call from any thread. */
public interface CodeHighlighter {
    /**
     * Returns spans sorted by start, non-overlapping, within bounds, and together covering
     * all of [source] (empty list for empty source). Unknown or null [language] yields one PLAIN span.
     */
    public fun tokenize(
        source: CharSequence,
        language: String?,
    ): List<TokenSpan>

    /** True when [language] (name, alias or info string) has a rule set. */
    public fun supports(language: String?): Boolean
}
