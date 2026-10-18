package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor

/**
 * Maps between anchors and spreads (ADR-0005). Pure functions of a [PageFlow]: the anchor is the only thing
 * that survives a geometry change, spreads are derived (ANC-01, ANC-03).
 */
public object AnchorMapper {
    /**
     * The anchor of [spread] (ANC-01): the first visible character of the leftmost visible column, or of the
     * rightmost one when [rtl] is true. Pages are placed in reading direction (PAG-12), so for a right to left
     * document the first page of the spread is the rightmost column and both cases name the same page: the one
     * that comes first in reading order. This holds only while the surface mirrors the columns for right to left
     * documents. Null when [spread] is out of range.
     */
    public fun leftmostVisibleAnchor(
        flow: PageFlow,
        spread: Int,
        rtl: Boolean = false,
    ): Anchor? {
        if (spread !in 0 until flow.spreadCount) return null
        val pages = flow.pagesOfSpread(spread)
        val wanted = if (rtl) flow.columnCount - 1 else 0
        for (i in pages.indices) {
            if (visualColumn(i, flow.columnCount, rtl) == wanted) return pages[i].anchor
        }
        return null
    }

    /** Index of the spread that shows [anchor], or -1 when [flow] has no pages (ANC-03). */
    public fun spreadContaining(
        flow: PageFlow,
        anchor: Anchor,
    ): Int = flow.spreadIndexOf(anchor)

    /**
     * Visual column, counted from the left edge, of the [indexInSpread]-th page (in reading order) of a spread of
     * [columnCount] columns: the same index for left to right documents, mirrored for right to left ones (PAG-12).
     */
    public fun visualColumn(
        indexInSpread: Int,
        columnCount: Int,
        rtl: Boolean,
    ): Int = if (rtl) columnCount - 1 - indexInSpread else indexInSpread
}
