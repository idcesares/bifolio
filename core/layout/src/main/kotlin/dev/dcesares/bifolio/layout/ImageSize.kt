package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.ImageBlock

/** Intrinsic pixel size of an image, as far as the app knows it (SYN-08, PAG-07). */
public sealed interface ImageSize {
    /**
     * Width and height of the image as displayed at scale 1, after any EXIF rotation, in layout px. The paginator
     * only ever scales it down.
     */
    public data class Known(
        val width: Int,
        val height: Int,
    ) : ImageSize {
        init {
            require(width > 0 && height > 0) { "image size must be positive: ${width}x$height" }
        }
    }

    /** Not read yet, unreadable, remote, rejected or not granted. The block takes the placeholder box. */
    public data object Unknown : ImageSize
}

/**
 * Where the paginator learns the size of an image (parallel to [BlockMeasurer]). Contract:
 * - sizes must be known BEFORE pagination: the answer for a block must not change during one [Paginator] run,
 *   so give a run an immutable snapshot (the same one to [Paginator] and [PageDetailer]);
 * - it is called on a background thread and must not block, decode or touch I/O: read a map filled by a
 *   bounds-only decode done earlier;
 * - [ImageSize.Unknown] gives the stable placeholder box of [BlockMeasurer.measureAtomic] for the block, so a failed
 *   decode never changes the layout once a size was reported; keep reporting the bounds you had;
 * - when sizes arrive later, run a new pagination with the new snapshot and a new `imageKey` (see [LayoutKey]),
 *   anchored at the current anchor (ANC-03).
 */
public fun interface ImageSizeProvider {
    public fun sizeOf(block: ImageBlock): ImageSize

    public companion object {
        /** Knows no size: every image is a placeholder. */
        public val UNKNOWN: ImageSizeProvider = ImageSizeProvider { ImageSize.Unknown }
    }
}

/** PAG-07 scaling: fit inside a column width and a maximum height, keeping the aspect ratio, never upscaling. */
internal object AtomicFit {
    fun fit(
        size: AtomicSize,
        maxWidth: Int,
        maxHeight: Int,
    ): AtomicSize {
        val w = maxOf(size.width, 1)
        val h = maxOf(size.height, 1)
        val mw = maxOf(maxWidth, 1)
        val mh = maxOf(maxHeight, 1)
        return when {
            w <= mw && h <= mh -> AtomicSize(w, h)
            // Width is the tighter bound when mw / w <= mh / h.
            mw.toLong() * h <= mh.toLong() * w -> AtomicSize(mw, scaled(h, mw, w))
            else -> AtomicSize(scaled(w, mh, h), mh)
        }
    }

    /** `round(value * num / den)`, at least 1. */
    private fun scaled(
        value: Int,
        num: Int,
        den: Int,
    ): Int = maxOf(1, ((value.toLong() * num * 2 + den) / (2L * den)).toInt())
}
