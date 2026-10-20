package dev.dcesares.bifolio.highlight

private const val TRIPLE = 3
private const val BACKSLASH = '\\'

/** Exclusive end of the string literal opening at [from]. Unterminated literals end at line or source end. */
internal fun stringEnd(
    src: CharSequence,
    spec: LanguageSpec,
    from: Int,
    quote: Char,
): Int {
    if (spec.tripleQuotes) {
        val triple = "$quote$quote$quote"
        if (src.startsWithAt(triple, from)) return tripleEnd(src, triple, from)
    }
    val multiline = spec.multilineQuotes.indexOf(quote) >= 0
    var j = from + 1
    while (j < src.length && src[j] != quote) {
        val c = src[j]
        if (c == '\n' && !multiline) break
        j += if (c == BACKSLASH && escapes(src, j)) 2 else 1
    }
    return if (j < src.length && src[j] == quote) j + 1 else minOf(j, src.length)
}

private fun escapes(
    src: CharSequence,
    at: Int,
): Boolean = at + 1 < src.length && src[at + 1] != '\n'

private fun tripleEnd(
    src: CharSequence,
    triple: String,
    from: Int,
): Int {
    var j = from + TRIPLE
    while (j < src.length && !src.startsWithAt(triple, j)) j += if (src[j] == BACKSLASH) 2 else 1
    return minOf(src.length, j + TRIPLE)
}
