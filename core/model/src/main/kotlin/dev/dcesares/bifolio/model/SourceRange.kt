package dev.dcesares.bifolio.model

/**
 * Half-open character range `[start, end)` into [Document.source] (DOC-07).
 * Offsets count UTF-16 code units of the line-ending-normalized source (DOC-04).
 */
public data class SourceRange(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 0) { "start must be >= 0: $start" }
        require(end >= start) { "end must be >= start: $start..$end" }
    }

    public val length: Int get() = end - start

    public operator fun contains(offset: Int): Boolean = offset >= start && offset < end

    public fun contains(other: SourceRange): Boolean = other.start >= start && other.end <= end
}
