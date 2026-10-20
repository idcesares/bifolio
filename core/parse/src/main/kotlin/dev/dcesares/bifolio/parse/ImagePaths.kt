package dev.dcesares.bifolio.parse

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Relative image paths (SYN-08, UI-14). Order: cut the query and fragment, refuse what is wrong in the raw text,
 * percent-decode exactly once, refuse what is wrong in the decoded text, then normalize against the base folder.
 */
internal object RelativePaths {
    fun resolve(
        destination: String,
        base: List<String>,
    ): ImageSource {
        val cut = destination.indexOfFirst { it == '?' || it == '#' }
        val raw = if (cut < 0) destination else destination.substring(0, cut)
        val rawProblem = rawProblem(raw)
        return if (rawProblem != null) ImageSource.Rejected(rawProblem) else resolveDecoded(raw, base)
    }

    private fun resolveDecoded(
        raw: String,
        base: List<String>,
    ): ImageSource {
        val decoded = PercentDecoding.decode(raw) ?: return ImageSource.Rejected(RejectReason.BAD_ENCODING)
        val decodedProblem = decodedProblem(decoded)
        if (decodedProblem != null) return ImageSource.Rejected(decodedProblem)
        val segments = normalize(decoded, base) ?: return ImageSource.Rejected(RejectReason.TRAVERSAL)
        return if (segments.isEmpty()) {
            ImageSource.Rejected(RejectReason.EMPTY)
        } else {
            ImageSource.Relative(segments)
        }
    }

    private fun rawProblem(raw: String): RejectReason? =
        when {
            raw.isEmpty() -> RejectReason.EMPTY
            raw.indexOf('\\') >= 0 -> RejectReason.BACKSLASH
            raw[0] == '/' -> RejectReason.ABSOLUTE_PATH
            else -> null
        }

    private fun decodedProblem(decoded: String): RejectReason? {
        val start =
            when {
                decoded.isEmpty() -> RejectReason.EMPTY
                decoded[0] == '/' -> RejectReason.ABSOLUTE_PATH
                else -> null
            }
        if (start != null) return start
        val inside = decoded.firstNotNullOfOrNull { charProblem(it) }
        if (inside != null) return inside
        val last = decoded.substringAfterLast('/')
        return if (last.isEmpty() || last == "." || last == "..") RejectReason.NOT_A_FILE else null
    }

    /**
     * A `%` left after one round of decoding is a double-encoded name (`%252e%252e`); a `:` is a scheme or an
     * NTFS stream.
     */
    private fun charProblem(c: Char): RejectReason? =
        when {
            c.code < SPACE || c.code == DELETE -> RejectReason.CONTROL_CHARACTER
            c == '\\' -> RejectReason.BACKSLASH
            c == ':' -> RejectReason.SCHEME
            c == '%' -> RejectReason.BAD_ENCODING
            else -> null
        }

    /** Segments from the granted root, or null when the path climbs above it. */
    private fun normalize(
        decoded: String,
        base: List<String>,
    ): List<String>? {
        val stack = ArrayList<String>(base)
        for (part in decoded.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack
    }

    private const val SPACE = 0x20
    private const val DELETE = 0x7f
}

/** One round of strict UTF-8 percent-decoding. */
internal object PercentDecoding {
    /** Decodes `%XX` as UTF-8 bytes. Null for a truncated or non-hex escape or for bytes that are not valid UTF-8. */
    fun decode(raw: String): String? {
        if (raw.indexOf('%') < 0) return raw
        val out = ByteArrayOutputStream(raw.length)
        var i = 0
        while (i < raw.length) {
            i = if (raw[i] == '%') copyEscape(raw, i, out) else copyLiteral(raw, i, out)
            if (i < 0) return null
        }
        return strictUtf8(out.toByteArray())
    }

    /** Writes the byte of the escape at [at]; returns the next index, or -1 for a bad escape. */
    private fun copyEscape(
        raw: String,
        at: Int,
        out: ByteArrayOutputStream,
    ): Int {
        val byte = escapeAt(raw, at)
        if (byte < 0) return -1
        out.write(byte)
        return at + ESCAPE_LENGTH
    }

    /** Writes the text up to the next `%`; returns the next index. */
    private fun copyLiteral(
        raw: String,
        at: Int,
        out: ByteArrayOutputStream,
    ): Int {
        val next = raw.indexOf('%', at).let { if (it < 0) raw.length else it }
        out.write(raw.substring(at, next).toByteArray(Charsets.UTF_8))
        return next
    }

    /** The byte of the `%XX` escape at [at], or -1 when it is truncated or not hex. */
    private fun escapeAt(
        raw: String,
        at: Int,
    ): Int {
        if (at + 2 >= raw.length) return -1
        val hi = hex(raw[at + 1])
        val lo = hex(raw[at + 2])
        return if (hi < 0 || lo < 0) -1 else hi * HEX_BASE + lo
    }

    private fun strictUtf8(bytes: ByteArray): String? =
        try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun hex(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + DECIMAL
            in 'A'..'F' -> c - 'A' + DECIMAL
            else -> -1
        }

    private const val HEX_BASE = 16
    private const val DECIMAL = 10
    private const val ESCAPE_LENGTH = 3
}

/** `data:image/...;base64,...` (SYN-08): length first, then MIME, then the base64 alphabet, never a decode. */
internal object DataUris {
    private val mimeTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp")

    fun parse(uri: String): ImageSource {
        val problem = headerProblem(uri)
        if (problem != null) return ImageSource.Rejected(problem)
        val comma = uri.indexOf(',')
        val size = decodedSize(uri, comma + 1)
        return if (size <= 0) {
            ImageSource.Rejected(RejectReason.DATA_MALFORMED)
        } else {
            ImageSource.DataUri(uri, mimeOf(uri, comma), comma + 1, size)
        }
    }

    private fun mimeOf(
        uri: String,
        comma: Int,
    ): String {
        val header = uri.substring(DATA_PREFIX.length, comma)
        return header.substringBefore(';').trim().lowercase()
    }

    /** Length first, so a huge payload is never scanned; then the comma, the MIME allow list and `;base64`. */
    private fun headerProblem(uri: String): RejectReason? {
        val comma = uri.indexOf(',')
        val header = if (comma < 0) emptyList() else uri.substring(DATA_PREFIX.length, comma).split(';')
        return when {
            uri.length > ImageSources.MAX_DATA_URI_CHARS -> RejectReason.DATA_TOO_LARGE
            comma < 0 -> RejectReason.DATA_MALFORMED
            mimeOf(uri, comma) !in mimeTypes -> RejectReason.DATA_NOT_IMAGE
            !header.last().trim().equals("base64", ignoreCase = true) -> RejectReason.DATA_MALFORMED
            else -> null
        }
    }

    /** Decoded byte count of the standard base64 payload from [start], or -1 when it is not valid base64. */
    private fun decodedSize(
        uri: String,
        start: Int,
    ): Int {
        var dataChars = 0
        var padding = 0
        for (i in start until uri.length) {
            val c = uri[i]
            when {
                c == '=' -> padding++
                padding > 0 || !isBase64(c) -> return -1
                else -> dataChars++
            }
        }
        val total = dataChars + padding
        val valid = padding <= 2 && (if (padding == 0) dataChars % GROUP != 1 else total % GROUP == 0)
        return if (valid) dataChars * BITS_PER_CHAR / BITS_PER_BYTE else -1
    }

    private fun isBase64(c: Char): Boolean = c.isAsciiLetter() || c in '0'..'9' || c == '+' || c == '/'

    private const val DATA_PREFIX = "data:"
    private const val GROUP = 4
    private const val BITS_PER_CHAR = 6
    private const val BITS_PER_BYTE = 8
}
