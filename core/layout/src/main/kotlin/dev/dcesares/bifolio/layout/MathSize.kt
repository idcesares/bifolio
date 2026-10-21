package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.MathBlock

/** Natural size of a rendered formula, as far as the app knows it (SYN-12, PAG-07). */
public sealed interface MathSize {
    /**
     * Width and height of the formula at scale 1 in layout px, as the math renderer measured it. The paginator
     * only ever scales it down (display math is atomic, PAG-07).
     */
    public data class Known(
        val width: Int,
        val height: Int,
    ) : MathSize {
        init {
            require(width > 0 && height > 0) { "math size must be positive: ${width}x$height" }
        }
    }

    /**
     * Not measured yet, rejected by the renderer or failed to parse. The block takes the stable placeholder box of
     * [BlockMeasurer.measureAtomic], the same box for every failure, so a late failure never moves the pages.
     */
    public data object Unknown : MathSize
}

/**
 * Where the paginator learns the size of a display formula (parallel to [ImageSizeProvider]). Contract:
 * - sizes must be known BEFORE pagination: the answer for a block must not change during one [Paginator] run,
 *   so give a run an immutable snapshot (the same one to [Paginator] and [PageDetailer]);
 * - it is called on a background thread and must not block, measure or touch I/O: read a map filled by the math
 *   renderer earlier (a failed formula is reported as [MathSize.Unknown] or as the size of its monospace fallback,
 *   and the answer is kept once given);
 * - when sizes arrive later, run a new pagination with the new snapshot and a new `mathKey` (see [LayoutKey]),
 *   anchored at the current anchor (ANC-03).
 *
 * Inline math (`Inline.InlineMath`) is not sized through this provider. Until the UI can measure formulas inside a
 * line, an inline formula is laid out as a run of its TeX source (one character per source character, code
 * style), exactly like inline code, so the paragraph breaks and the character count stay stable.
 */
public fun interface MathSizeProvider {
    public fun sizeOf(block: MathBlock): MathSize

    public companion object {
        /** Knows no size: every display formula is a placeholder. */
        public val UNKNOWN: MathSizeProvider = MathSizeProvider { MathSize.Unknown }
    }
}
