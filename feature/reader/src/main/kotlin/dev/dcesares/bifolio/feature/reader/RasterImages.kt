package dev.dcesares.bifolio.feature.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import dev.dcesares.bifolio.data.OpenedFile
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/** The EXIF orientation values 1 to 8 (TIFF 6.0): which way a stored image must be turned to be shown upright. */
internal object ImageOrientation {
    const val NORMAL = 1

    /** Orientations 5 to 8 turn the image a quarter, so the shown width is the stored height. */
    fun swapsAxes(orientation: Int): Boolean = orientation in TRANSPOSE..ROTATE_270

    /** The matrix that makes a stored bitmap upright, or null when it already is (or the value is unknown). */
    fun matrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ROTATE_180 -> m.setRotate(HALF_TURN)
            FLIP_VERTICAL -> {
                m.setRotate(HALF_TURN)
                m.postScale(-1f, 1f)
            }
            TRANSPOSE -> {
                m.setRotate(QUARTER_TURN)
                m.postScale(-1f, 1f)
            }
            ROTATE_90 -> m.setRotate(QUARTER_TURN)
            TRANSVERSE -> {
                m.setRotate(-QUARTER_TURN)
                m.postScale(-1f, 1f)
            }
            ROTATE_270 -> m.setRotate(-QUARTER_TURN)
            else -> return null
        }
        return m
    }

    private const val FLIP_HORIZONTAL = 2
    private const val ROTATE_180 = 3
    private const val FLIP_VERTICAL = 4
    private const val TRANSPOSE = 5
    private const val ROTATE_90 = 6
    private const val TRANSVERSE = 7
    private const val ROTATE_270 = 8
    private const val HALF_TURN = 180f
    private const val QUARTER_TURN = 90f
}

/** What a header-only read learned about an image. [width] and [height] are as shown, after the EXIF turn. */
internal class ImageProbe(
    val format: ImageFormat,
    val width: Int,
    val height: Int,
    val orientation: Int,
)

/**
 * Reads and decodes raster images with the platform codecs only (SYN-08): no image library and no network
 * (PRV-01 to PRV-03). [open] may be called several times; each call must give a fresh stream. Every function
 * returns null instead of throwing for a file that is missing, damaged or not a supported image.
 */
internal object RasterImages {
    /** A header may claim any size; refuse what could not be shown anyway (decompression bombs). */
    const val MAX_PIXELS: Long = 100_000_000L

    fun probe(open: () -> OpenedFile?): ImageProbe? =
        try {
            probeUnsafe(open)
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    /**
     * Decodes the image shown at about [targetWidth] x [targetHeight] px: subsampled by a power of two as far as
     * that keeps both sides at least as large as the target, then turned upright. Null on any failure.
     */
    fun decode(
        open: () -> OpenedFile?,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? =
        try {
            decodeUnsafe(open, targetWidth, targetHeight)
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }

    private fun probeUnsafe(open: () -> OpenedFile?): ImageProbe? {
        val file = open() ?: return null
        val header = file.stream.use { readHeader(it, file.mimeType) } ?: return null
        val (format, raw) = header
        val (w, h) = raw
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null
        val upright = format == ImageFormat.GIF || format == ImageFormat.BMP
        val orientation = if (upright) ImageOrientation.NORMAL else orientationOf(open)
        return if (ImageOrientation.swapsAxes(orientation)) {
            ImageProbe(format, h, w, orientation)
        } else {
            ImageProbe(format, w, h, orientation)
        }
    }

    /** The verified format and the stored size, or null when the bytes are not an image of the declared type. */
    private fun readHeader(
        stream: InputStream,
        mimeType: String?,
    ): Pair<ImageFormat, Pair<Int, Int>>? {
        val input = BufferedInputStream(stream)
        input.mark(ImageSniffer.HEAD)
        val head = ByteArray(ImageSniffer.HEAD)
        val read = readFully(input, head)
        input.reset()
        val format = ImageSniffer.verify(head.copyOf(read), mimeType) ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        if (options.outMimeType != null && options.outMimeType != format.mime) return null
        return format to (options.outWidth to options.outHeight)
    }

    @Suppress("ReturnCount") // Each early return is one way the file can fail to be an image.
    private fun decodeUnsafe(
        open: () -> OpenedFile?,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val probe = probeUnsafe(open) ?: return null
        val wantedWidth = targetWidth.coerceAtLeast(1)
        val wantedHeight = targetHeight.coerceAtLeast(1)
        var sample = 1
        while (probe.width / (sample * 2) >= wantedWidth && probe.height / (sample * 2) >= wantedHeight) sample *= 2
        val file = open() ?: return null
        val decoded =
            file.stream.use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeStream(BufferedInputStream(stream), null, options)
            } ?: return null
        val matrix = ImageOrientation.matrix(probe.orientation) ?: return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    private fun orientationOf(open: () -> OpenedFile?): Int {
        val file = open() ?: return ImageOrientation.NORMAL
        return try {
            file.stream.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ImageOrientation.NORMAL)
            }
        } catch (_: IOException) {
            ImageOrientation.NORMAL
        }
    }

    private fun readFully(
        input: InputStream,
        into: ByteArray,
    ): Int {
        var total = 0
        while (total < into.size) {
            val n = input.read(into, total, into.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
