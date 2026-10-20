@file:Suppress("TooManyFunctions")

package dev.dcesares.bifolio.feature.reader

import java.net.IDN
import java.util.Locale

private const val MAX_PORT = 65_535
private const val MAX_PORT_DIGITS = 5
private val AllowedSchemes = setOf("http", "https", "mailto")
private const val HOST_FORBIDDEN = "<>\"{}|^`\\%@[] "
private const val PUNYCODE_PREFIX = "xn--"
private const val ASCII_MAX = 0x7f

/**
 * An external link that passed the checks of NAV-08, in the one form that is both shown and opened. It can only be
 * made by [parseExternalLink], so code that holds one never has to ask again whether the scheme is allowed.
 *
 * - [url]: scheme in lowercase, backslashes before the query read as slashes, and an international host in its ASCII
 *   (punycode) form, so what the reader confirms is what the browser receives.
 * - [hostRanges]: where the real host is in [url] (the domain of each address for mail). Anything before it, such as
 *   `good.com@` user information, is not the host.
 * - [unicodeHost]: the host as it reads in Unicode, only when [isInternational].
 */
internal class ExternalLink private constructor(
    val url: String,
    val scheme: String,
    val hostRanges: List<IntRange>,
    val hasUserInfo: Boolean,
    val unicodeHost: String?,
) {
    val isInternational: Boolean get() = unicodeHost != null

    /** The host texts, for tests and for the accessibility description. */
    val hostTexts: List<String> get() = hostRanges.map { url.substring(it) }

    internal companion object {
        fun of(
            url: String,
            scheme: String,
            hostRanges: List<IntRange>,
            hasUserInfo: Boolean,
            unicodeHost: String?,
        ) = ExternalLink(url, scheme, hostRanges, hasUserInfo, unicodeHost)
    }
}

/**
 * Checks [raw] for NAV-08. Returns null for anything that may not be opened: a scheme other than http, https or
 * mailto, a web address without a host, a malformed port, spaces, control or invisible formatting characters
 * (right-to-left overrides and zero-width characters hide where a link goes), or a host that is not valid.
 */
internal fun parseExternalLink(raw: String): ExternalLink? {
    val text = raw.trim()
    if (text.isEmpty() || text.any { it.isHidden() }) return null
    val colon = text.indexOf(':')
    if (colon <= 0) return null
    val scheme = text.substring(0, colon).lowercase(Locale.ROOT)
    if (scheme !in AllowedSchemes) return null
    val rest = text.substring(colon + 1)
    return if (scheme == "mailto") mailLink(rest) else webLink(scheme, rest)
}

private fun Char.isHidden(): Boolean =
    Character.isISOControl(this) ||
        Character.isWhitespace(this) ||
        Character.isSpaceChar(this) ||
        Character.getType(this).let {
            it == Character.FORMAT.toInt() ||
                it == Character.LINE_SEPARATOR.toInt() ||
                it == Character.PARAGRAPH_SEPARATOR.toInt()
        }

private fun mailLink(rest: String): ExternalLink? {
    if (rest.isEmpty()) return null
    val url = "mailto:$rest"
    val addresses = rest.substringBefore('?')
    val ranges = ArrayList<IntRange>(1)
    var from = 0
    for (address in addresses.split(',')) {
        val at = address.lastIndexOf('@')
        if (at >= 0 && at + 1 < address.length) {
            val start = "mailto:".length + from + at + 1
            ranges.add(start until start + address.length - at - 1)
        }
        from += address.length + 1
    }
    return ExternalLink.of(url, "mailto", ranges, hasUserInfo = false, unicodeHost = null)
}

private fun webLink(
    scheme: String,
    rest: String,
): ExternalLink? {
    if (!rest.startsWith("//")) return null
    // Browsers read a backslash before the query as a slash, so do the same before measuring anything.
    val cut = rest.indexOfFirst { it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
    val read = rest.substring(0, cut).replace('\\', '/') + rest.substring(cut)
    val afterSlashes = 2
    val end =
        read.indexOfFirst(afterSlashes) { it == '/' || it == '?' || it == '#' }.let {
            if (it <
                0
            ) {
                read.length
            } else {
                it
            }
        }
    val authority = read.substring(afterSlashes, end)
    val at = authority.lastIndexOf('@')
    val hostPort = authority.substring(at + 1)
    val host = hostOf(hostPort) ?: return null
    val hostStart = afterSlashes + at + 1
    val prefix = "$scheme://" + read.substring(afterSlashes, hostStart)
    val suffix = read.substring(hostStart + host.length)
    return finish(scheme, prefix, host, suffix, hasUserInfo = at >= 0)
}

private inline fun String.indexOfFirst(
    from: Int,
    predicate: (Char) -> Boolean,
): Int {
    for (i in from until length) if (predicate(this[i])) return i
    return -1
}

private fun finish(
    scheme: String,
    prefix: String,
    host: String,
    suffix: String,
    hasUserInfo: Boolean,
): ExternalLink? {
    val ipLiteral = host.startsWith('[')
    val needsIdn =
        !ipLiteral && (host.any { it.code > ASCII_MAX } || host.lowercase(Locale.ROOT).contains(PUNYCODE_PREFIX))
    if (!needsIdn) {
        val range = prefix.length until prefix.length + host.length
        return ExternalLink.of(prefix + host + suffix, scheme, listOf(range), hasUserInfo, null)
    }
    val ascii = asciiOf(host) ?: return null
    val unicode =
        try {
            IDN.toUnicode(ascii)
        } catch (_: IllegalArgumentException) {
            return null
        }
    // A host written in punycode that is not really international (xn-- followed by plain ASCII) is not flagged.
    val flagged = unicode.any { it.code > ASCII_MAX }
    val range = prefix.length until prefix.length + ascii.length
    return ExternalLink.of(prefix + ascii + suffix, scheme, listOf(range), hasUserInfo, if (flagged) unicode else null)
}

private fun asciiOf(host: String): String? =
    try {
        IDN.toASCII(host)
    } catch (_: IllegalArgumentException) {
        null
    }

/** The host of `host`, `host:port`, `[v6]` or `[v6]:port`, or null when it is empty or not valid. Keeps its case. */
private fun hostOf(hostPort: String): String? =
    when {
        hostPort.isEmpty() -> null
        hostPort.startsWith('[') -> literalHostOf(hostPort)
        else -> namedHostOf(hostPort)
    }

private fun literalHostOf(hostPort: String): String? {
    val close = hostPort.indexOf(']')
    val literal = if (close < 0) null else hostPort.substring(0, close + 1)
    val inner = literal?.substring(1, literal.length - 1).orEmpty()
    val valid = inner.isNotEmpty() && inner.all(::isLiteralChar) && portOk(hostPort.substring(close + 1))
    return if (valid) literal else null
}

private fun isLiteralChar(c: Char): Boolean = c.isDigit() || c in 'a'..'f' || c in 'A'..'F' || c == ':' || c == '.'

private fun namedHostOf(hostPort: String): String? {
    val colon = hostPort.lastIndexOf(':')
    val host = if (colon >= 0) hostPort.substring(0, colon) else hostPort
    val portValid = colon < 0 || portOk(hostPort.substring(colon))
    val valid = host.isNotEmpty() && host.none { it in HOST_FORBIDDEN || it == ':' }
    return if (portValid && valid) host else null
}

/** [text] is empty or `:` followed by up to five digits not above 65535. */
private fun portOk(text: String): Boolean {
    if (text.isEmpty()) return true
    if (text[0] != ':') return false
    val digits = text.substring(1)
    if (digits.isEmpty()) return true
    return digits.length <= MAX_PORT_DIGITS && digits.all { it in '0'..'9' } && digits.toInt() <= MAX_PORT
}
