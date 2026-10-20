package dev.dcesares.bifolio.feature.reader

/** Raster formats the reader decodes (SYN-08). SVG and everything else is refused, never decoded. */
internal enum class ImageFormat(
    val mime: String,
) {
    PNG("image/png"),
    JPEG("image/jpeg"),
    GIF("image/gif"),
    WEBP("image/webp"),
    BMP("image/bmp"),
}

/** Decides what a file really is from its first bytes, and whether that agrees with the type it was declared as. */
internal object ImageSniffer {
    /** Enough bytes to tell every [ImageFormat] apart. */
    const val HEAD = 16

    private val pngMagic = intArrayOf(0x89, 'P'.code, 'N'.code, 'G'.code, 0x0D, 0x0A, 0x1A, 0x0A)
    private val jpegMagic = intArrayOf(JPEG_FIRST, JPEG_SECOND, JPEG_THIRD)

    /** The format whose magic bytes start [head], or null (SVG and XML included). */
    fun sniff(head: ByteArray): ImageFormat? =
        when {
            startsWith(head, pngMagic) -> ImageFormat.PNG
            startsWith(head, jpegMagic) -> ImageFormat.JPEG
            startsWith(head, "GIF87a".map { it.code }.toIntArray()) ||
                startsWith(head, "GIF89a".map { it.code }.toIntArray()) -> ImageFormat.GIF
            isWebp(head) -> ImageFormat.WEBP
            startsWith(head, intArrayOf('B'.code, 'M'.code)) -> ImageFormat.BMP
            else -> null
        }

    /**
     * The format of [head] when it agrees with [declaredMime]; null when the bytes are not a supported image or the
     * declared type names another one. A missing or generic declared type (`application/octet-stream`, which
     * providers give to files they know nothing about) leaves the bytes to decide.
     */
    fun verify(
        head: ByteArray,
        declaredMime: String?,
    ): ImageFormat? {
        val format = sniff(head) ?: return null
        val declared = declaredMime?.substringBefore(';')?.trim()?.lowercase()
        if (declared.isNullOrEmpty() || declared == GENERIC) return format
        val normalized = if (declared == "image/jpg" || declared == "image/pjpeg") ImageFormat.JPEG.mime else declared
        return format.takeIf { it.mime == normalized }
    }

    private fun isWebp(head: ByteArray): Boolean =
        head.size >= WEBP_TAG_END &&
            startsWith(head, "RIFF".map { it.code }.toIntArray()) &&
            String(head, WEBP_TAG_START, WEBP_TAG_END - WEBP_TAG_START, Charsets.ISO_8859_1) == "WEBP"

    private fun startsWith(
        bytes: ByteArray,
        magic: IntArray,
    ): Boolean = bytes.size >= magic.size && magic.indices.all { (bytes[it].toInt() and BYTE_MASK) == magic[it] }

    private const val BYTE_MASK = 0xFF
    private const val JPEG_FIRST = 0xFF
    private const val JPEG_SECOND = 0xD8
    private const val JPEG_THIRD = 0xFF
    private const val GENERIC = "application/octet-stream"
    private const val WEBP_TAG_START = 8
    private const val WEBP_TAG_END = 12
}
