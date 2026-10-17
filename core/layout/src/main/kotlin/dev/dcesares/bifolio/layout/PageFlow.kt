package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor

/**
 * Immutable snapshot of a pagination (PAG-01, PAG-09).
 *
 * - [provisional]: the pages were composed starting at the reading anchor, before the pages in front of it
 *   were known. Page breaks may differ from the final pagination, but the anchor is on the first page.
 * - [complete]: every page of the document is present.
 * A snapshot that is neither provisional nor complete is the canonical pagination from the document start,
 * still being extended in the background.
 */
public class PageFlow internal constructor(
    public val pages: List<Page>,
    public val columnCount: Int,
    public val provisional: Boolean,
    public val complete: Boolean,
    /** Fraction of the source text consumed by [pages], 0 to 1. Used for the estimate only. */
    public val consumed: Float,
) {
    /** Exact when [complete], otherwise extrapolated from [consumed] (PAG-09). */
    public val estimatedPageCount: Int
        get() =
            when {
                complete || consumed <= 0f -> pages.size
                else -> maxOf(pages.size, (pages.size / consumed).toInt())
            }

    /** Number of spreads of [pages]: a spread advances by [columnCount] pages (PAG-12). */
    public val spreadCount: Int get() = (pages.size + columnCount - 1) / columnCount

    /**
     * Index of the page that shows [anchor]: the last page whose own anchor is at or before it (PAG-11), or
     * -1 when there are no pages. An anchor past the end of the document maps to the last page.
     */
    public fun pageIndexOf(anchor: Anchor): Int {
        var lo = 0
        var hi = pages.size - 1
        var found = if (pages.isEmpty()) -1 else 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (pages[mid].anchor.compareReadingOrder(anchor) <= 0) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** Spread that shows [anchor], or -1 when there are no pages. */
    public fun spreadIndexOf(anchor: Anchor): Int = pageIndexOf(anchor).let { if (it < 0) -1 else it / columnCount }

    /** Pages of [spread]; the final spread may hold fewer than [columnCount] pages (empty columns). */
    public fun pagesOfSpread(spread: Int): List<Page> =
        pages.subList(
            spread * columnCount,
            minOf(
                (spread + 1) * columnCount,
                pages.size,
            ),
        )
}
