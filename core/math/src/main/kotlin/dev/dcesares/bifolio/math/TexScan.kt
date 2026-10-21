package dev.dcesares.bifolio.math

// Low level reading of TeX source: balanced groups and control sequences. All loops, no recursion.

/** Index after the control sequence that starts at [start] (a backslash): letters, or one other character. */
internal fun controlEnd(
    src: String,
    start: Int,
): Int {
    var i = start + 1
    if (i >= src.length) return src.length
    if (!src[i].isAsciiLetter()) return i + 1
    while (i < src.length && src[i].isAsciiLetter()) i++
    return i
}

internal fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/** Name of the control sequence at [start] without the backslash and without a trailing star, or "". */
internal fun controlName(
    src: String,
    start: Int,
): String = src.substring(start + 1, controlEnd(src, start).coerceAtMost(src.length))

/** Index after the `}` that closes the `{` at [open], or -1 when the group never closes. Escapes are skipped. */
internal fun groupEnd(
    src: String,
    open: Int,
): Int {
    var depth = 0
    var i = open
    while (i < src.length) {
        when (src[i]) {
            '\\' -> i = controlEnd(src, i) - 1
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return i + 1
            }
        }
        i++
    }
    return -1
}

/** Index of the first character at or after [from] that is not TeX white space. */
internal fun skipSpace(
    src: String,
    from: Int,
): Int {
    var i = from
    while (i < src.length && src[i].isWhitespace()) i++
    return i
}

/** Index after the `[...]` that starts at [from] (after spaces), or [from] when there is none. */
internal fun skipOptional(
    src: String,
    from: Int,
): Int {
    val i = skipSpace(src, from)
    if (i >= src.length || src[i] != '[') return from
    val close = src.indexOf(']', i)
    return if (close < 0) from else close + 1
}

/**
 * Index after one operand that starts at or after [from]: a group, a control sequence or one character. Null when
 * the source ends first, or when the next token closes a group or a cell (the operand is missing).
 */
internal fun operandEnd(
    src: String,
    from: Int,
): Int? {
    val i = skipSpace(src, from)
    if (i >= src.length) return null
    return when (src[i]) {
        '}', '&' -> null
        '{' -> groupEnd(src, i).takeIf { it > 0 }
        '\\' -> if (src.startsWith("\\\\", i) || isEnd(src, i)) null else controlEnd(src, i)
        else -> i + 1
    }
}

private fun isEnd(
    src: String,
    at: Int,
): Boolean = controlName(src, at).let { it == "end" || it == "right" }

/** Content of the group at [open] without its braces, or null when it is not a closed group. */
internal fun groupText(
    src: String,
    open: Int,
): String? {
    if (open >= src.length || src[open] != '{') return null
    val end = groupEnd(src, open)
    return if (end < 0) null else src.substring(open + 1, end - 1)
}

/** A character that can be part of a bare TeX length such as `-1.5em`. */
internal fun isLengthChar(c: Char): Boolean = c in ".+-" || c.isDigit() || c.isAsciiLetter()
