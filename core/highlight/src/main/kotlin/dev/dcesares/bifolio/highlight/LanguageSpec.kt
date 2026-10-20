package dev.dcesares.bifolio.highlight

/** Declarative rules for the shared scanner. Scanners never backtrack, so cost is linear. */
internal class LanguageSpec(
    val keywords: Set<String>,
    val types: Set<String> = emptySet(),
    val caseInsensitive: Boolean = false,
    val lineComments: List<String> = emptyList(),
    val blockOpen: String? = null,
    val blockClose: String? = null,
    val quotes: String = "\"'",
    val multilineQuotes: String = "",
    val tripleQuotes: Boolean = false,
    val identStartExtra: String = "",
    val identPartExtra: String = "",
    val capitalTypes: Boolean = true,
    val keyMarker: Char? = null,
    val dollarVars: Boolean = false,
) {
    val maxWord: Int = (keywords + types).maxOfOrNull { it.length } ?: 0

    fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_' || identStartExtra.indexOf(c) >= 0

    fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || identPartExtra.indexOf(c) >= 0

    fun startsLineComment(
        src: CharSequence,
        at: Int,
    ): Boolean = lineComments.any { src.startsWithAt(it, at) }
}

internal fun words(list: String): Set<String> = list.split(' ', '\n').filter { it.isNotEmpty() }.toSet()

internal const val C_LINE = "//"
internal const val C_OPEN = "/*"
internal const val C_CLOSE = "*/"
