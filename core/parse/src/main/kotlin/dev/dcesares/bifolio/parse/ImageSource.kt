package dev.dcesares.bifolio.parse

import java.util.Base64

/** Why an image destination was refused (SYN-08, PRV-01 to PRV-03). The reader shows the alt text instead. */
public enum class RejectReason {
    EMPTY,
    CONTROL_CHARACTER,
    BACKSLASH,
    BAD_ENCODING,
    ABSOLUTE_PATH,
    TRAVERSAL,
    NOT_A_FILE,

    /** A scheme other than `data:`, `http:`, `https:` (`file:`, `javascript:`, `android_asset:`, a drive letter...). */
    SCHEME,

    /** A `content:` URI written in the document text (never loadable unless the caller opts in). */
    CONTENT_URI,
    DATA_TOO_LARGE,
    DATA_NOT_IMAGE,
    DATA_MALFORMED,
}

/**
 * Where an image destination points, decided from its text alone. Nothing here touches the file system or the
 * network; the reader app loads [Relative], [DataUri] and [ContentUri] and shows the alt text for the rest.
 */
public sealed interface ImageSource {
    /**
     * A base64 image data URI that passed the size, MIME and alphabet checks. Holds the original string and where
     * the payload starts (no copy); [decode] allocates the bytes.
     */
    public data class DataUri(
        val uri: String,
        val mimeType: String,
        val payloadStart: Int,
        /** Size of the decoded image in bytes, computed from the payload length. */
        val decodedSize: Int,
    ) : ImageSource {
        public fun decode(): ByteArray {
            val payload = uri.substring(payloadStart)
            return Base64.getDecoder().decode(payload)
        }
    }

    /**
     * A file below the granted folder: [segments] run from the granted root, already percent-decoded and
     * normalized. They never contain `.`, `..`, empty names, `/`, `\`, `:`, `%` or control characters.
     */
    public data class Relative(
        val segments: List<String>,
    ) : ImageSource {
        public val path: String get() = segments.joinToString("/")
    }

    /** A `content:` URI. Only produced when the caller passes `allowContentUri` (see [ImageSources.classify]). */
    public data class ContentUri(
        val uri: String,
    ) : ImageSource

    /** `http:`, `https:` or `//host`. Never fetched (PRV-01); the reader shows the alt text. */
    public data class Remote(
        val url: String,
    ) : ImageSource

    public data class Rejected(
        val reason: RejectReason,
    ) : ImageSource
}

/** Classifies the destination of a Markdown image (SYN-08) without any I/O. Pure and thread-safe. */
public object ImageSources {
    /** Longest accepted data URI, in characters of the encoded string (2 MiB), checked before any decoding. */
    public const val MAX_DATA_URI_CHARS: Int = 2 * 1024 * 1024

    /**
     * [destination] is the link destination of the image as the parser reports it. [base] holds the folder names
     * from the granted root down to the folder of the document (empty when the document sits in the granted
     * folder), so `../images/a.png` works from `docs/` while nothing can leave the granted root.
     * [allowContentUri] is false in v1: a `content:` URI inside document text is rejected.
     */
    public fun classify(
        destination: String,
        base: List<String> = emptyList(),
        allowContentUri: Boolean = false,
    ): ImageSource {
        val d = destination.trim()
        val problem = basicProblem(d)
        if (problem != null) return ImageSource.Rejected(problem)
        val scheme = schemeOf(d)
        return when {
            scheme == null -> if (d.startsWith("//")) ImageSource.Remote(d) else RelativePaths.resolve(d, base)
            scheme.equals("data", ignoreCase = true) -> DataUris.parse(d)
            scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true) ->
                ImageSource.Remote(d)
            scheme.equals("content", ignoreCase = true) ->
                if (allowContentUri) ImageSource.ContentUri(d) else ImageSource.Rejected(RejectReason.CONTENT_URI)
            else -> ImageSource.Rejected(RejectReason.SCHEME)
        }
    }

    private fun basicProblem(d: String): RejectReason? {
        if (d.isEmpty()) return RejectReason.EMPTY
        for (c in d) if (c.code < SPACE || c.code == DELETE) return RejectReason.CONTROL_CHARACTER
        return null
    }

    /** RFC 3986 scheme at the start of [d], or null when the text is a relative reference. */
    private fun schemeOf(d: String): String? {
        if (!d[0].isAsciiLetter()) return null
        for (i in 1 until d.length) {
            val c = d[i]
            if (c == ':') return d.substring(0, i)
            if (!isSchemeChar(c)) return null
        }
        return null
    }

    private fun isSchemeChar(c: Char): Boolean = c.isAsciiLetter() || c in "0123456789+-."

    private const val SPACE = 0x20
    private const val DELETE = 0x7f
}

internal fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
