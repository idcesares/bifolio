package dev.dcesares.bifolio.layout

/** Recognition of Markdown character references (`&amp;`, `&#35;`, `&#x23;`) inside a source window. */
internal object Entities {
    private const val MAX_NAME = 32
    private const val MAX_DECIMAL = 7
    private const val MAX_HEX = 6
    private const val HEX = 16
    private const val DECIMAL = 10
    private const val PREFIX = 2
    const val MAX_CHARS = 2

    /** Index of the `;` that closes the reference starting with `&` at [at], or -1 when there is none. */
    fun end(
        source: String,
        at: Int,
        to: Int,
    ): Int {
        var m = at + 1
        if (m >= to) return -1
        val numeric = source[m] == '#'
        val hex = numeric && m + 1 < to && source[m + 1].lowercaseChar() == 'x'
        if (numeric) m += if (hex) 2 else 1
        val first = m
        val max = maxLength(numeric, hex)
        while (m < to && m - first <= max && isBodyChar(source[m], numeric, hex)) m++
        val well = m > first && m - first <= max && m < to
        return if (well && source[m] == ';') m else -1
    }

    private fun maxLength(
        numeric: Boolean,
        hex: Boolean,
    ): Int =
        when {
            hex -> MAX_HEX
            numeric -> MAX_DECIMAL
            else -> MAX_NAME
        }

    private fun isBodyChar(
        c: Char,
        numeric: Boolean,
        hex: Boolean,
    ): Boolean =
        when {
            !numeric -> c.isAsciiLetterOrDigit()
            hex -> c.isAsciiLetterOrDigit() && c.lowercaseChar() <= 'f'
            else -> c in '0'..'9'
        }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /**
     * Rendered chars (UTF-16 units) produced by the reference between [at] and [end] of [source]. The
     * [text] position [i] is where they start; a named reference gives one or two units, told apart by
     * the character that follows it.
     */
    fun chars(
        source: String,
        at: Int,
        end: Int,
        to: Int,
        text: String,
        i: Int,
    ): Int {
        val remaining = text.length - i
        if (source[at + 1] == '#') return minOf(remaining, numericChars(source, at, end))
        val next = if (end + 1 < to) source[end + 1] else null
        return when {
            next == null -> minOf(remaining, MAX_CHARS)
            remaining > 1 && text[i + 1] == next -> 1
            remaining > MAX_CHARS && text[i + MAX_CHARS] == next -> MAX_CHARS
            else -> 1
        }
    }

    private fun numericChars(
        source: String,
        at: Int,
        end: Int,
    ): Int {
        val hex = source[at + PREFIX].lowercaseChar() == 'x'
        val digits = source.substring(at + PREFIX + (if (hex) 1 else 0), end)
        val cp = digits.toIntOrNull(if (hex) HEX else DECIMAL) ?: return 1
        val surrogate = cp in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code
        return if (cp in 1..Character.MAX_CODE_POINT && !surrogate) Character.charCount(cp) else 1
    }
}
