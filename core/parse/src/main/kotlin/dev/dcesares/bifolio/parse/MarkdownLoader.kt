package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Document

/** How a file of a given size is opened (DOC-05). */
public enum class SizeClass { NORMAL, SCROLL_ONLY, REFUSED }

/** Size thresholds of DOC-05. "MB" is read as MiB (1024 * 1024 bytes). */
public object SizePolicy {
    private const val MIB = 1024L * 1024L

    /** Files larger than this open in scroll mode without pagination. */
    public const val SCROLL_ONLY_ABOVE_BYTES: Long = 8 * MIB

    /** Files larger than this are refused. */
    public const val REFUSE_ABOVE_BYTES: Long = 32 * MIB

    public fun classify(sizeBytes: Long): SizeClass =
        when {
            sizeBytes > REFUSE_ABOVE_BYTES -> SizeClass.REFUSED
            sizeBytes > SCROLL_ONLY_ABOVE_BYTES -> SizeClass.SCROLL_ONLY
            else -> SizeClass.NORMAL
        }
}

/** Result of loading a file's bytes. */
public sealed interface LoadResult {
    public data class Loaded(
        val document: Document,
        val sizeClass: SizeClass,
        val encoding: SourceEncoding,
        /** True when the Windows-1252 fallback was used and characters may be wrong (DOC-03). */
        val encodingWarning: Boolean,
    ) : LoadResult

    /** The file is above the refusal limit (DOC-05). Nothing was decoded. */
    public data class Refused(
        val sizeBytes: Long,
    ) : LoadResult
}

/** Bytes to [Document]: size policy, decoding, line-ending normalization and parsing. Thread-safe. */
public object MarkdownLoader {
    /**
     * Loads [bytes]. [declaredSize] lets a caller that knows the size before reading everything
     * (for example from the provider) refuse early; by default it is the array size.
     */
    public fun load(
        bytes: ByteArray,
        declaredSize: Long = bytes.size.toLong(),
    ): LoadResult {
        val sizeClass = SizePolicy.classify(declaredSize)
        if (sizeClass == SizeClass.REFUSED) return LoadResult.Refused(declaredSize)
        val decoded = TextDecoder.decode(bytes)
        val document = MarkdownParser.parse(decoded.text)
        return LoadResult.Loaded(document, sizeClass, decoded.encoding, decoded.encodingWarning)
    }
}
