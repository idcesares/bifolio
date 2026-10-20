package dev.dcesares.bifolio.highlight

/** Collects contiguous spans, merging neighbours of the same kind. Callers emit in order. */
internal class SpanSink {
    private val spans = ArrayList<TokenSpan>()

    fun add(
        start: Int,
        end: Int,
        kind: TokenKind,
    ) {
        if (end <= start) return
        val last = spans.lastOrNull()
        if (last != null && last.kind == kind && last.end == start) {
            spans[spans.size - 1] = TokenSpan(last.start, end, kind)
        } else {
            spans.add(TokenSpan(start, end, kind))
        }
    }

    fun result(): List<TokenSpan> = spans
}

internal fun CharSequence.startsWithAt(
    prefix: String,
    at: Int,
): Boolean {
    if (at + prefix.length > length) return false
    for (k in prefix.indices) if (this[at + k] != prefix[k]) return false
    return true
}

internal fun CharSequence.lineEnd(from: Int): Int {
    var j = from
    while (j < length && this[j] != '\n') j++
    return j
}
