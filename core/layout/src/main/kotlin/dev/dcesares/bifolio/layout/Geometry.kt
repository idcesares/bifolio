package dev.dcesares.bifolio.layout

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Spacing used while flowing blocks into columns, all in px at the current density and font size.
 * Part of [Geometry], so a change of any value is a different layout in the cache (PAG-10).
 */
public data class FlowMetrics(
    /** Vertical gap between top-level blocks. */
    val blockGap: Int = 16,
    /** Vertical gap between blocks nested in a quote, callout or loose list item. */
    val innerGap: Int = 8,
    /** Horizontal space taken by a list marker. */
    val listIndent: Int = 24,
    /** Horizontal space taken by a quote rule. */
    val quoteIndent: Int = 16,
    /** Height of a horizontal rule block (SYN-15). */
    val ruleHeight: Int = 24,
    /** Padding inside a table cell, per side, horizontal and vertical. */
    val tableCellPadding: Int = 6,
    /** Narrowest wrapped table column before the table becomes a horizontally scrolling figure (PAG-06). */
    val tableMinColumn: Int = 48,
    /** Maximum height of an atomic block as a percentage of the page height (PAG-07). */
    val atomicMaxHeightPercent: Int = 85,
    /** Horizontal padding inside the code surface, per side. */
    val codePadding: Int = 6,
)

/**
 * Everything the geometry depends on, taken from the window and the reading settings, never from the
 * device (LAY-01). Pure values in px.
 */
public data class WindowSpec(
    /** Usable window width after insets. */
    val widthPx: Int,
    /** Usable window height after insets. */
    val heightPx: Int,
    val density: Float,
    /** Width of one character of the body face at the current size (the `charW` of LAY-04). */
    val charWidthPx: Float,
    /** User margin setting: 0.75, 1.0 or 1.35 (LAY-05). */
    val marginScale: Float = 1f,
    /** User column choice 1, 2 or 3, or null for automatic (LAY-08). */
    val forcedColumns: Int? = null,
    val metrics: FlowMetrics = FlowMetrics(),
    /** Opaque key of every text setting the measurer depends on (face, size, line height, hyphenation). */
    val typographyKey: Int = 0,
    /** Folding feature in window coordinates, or null. One that misses the window is ignored (LAY-01). */
    val fold: FoldSpec? = null,
    /**
     * Body font size times line height, in px. Only the tabletop minimum region (POS-04, four body lines) reads
     * it; 0 means unknown and the minimum is then 1 px.
     */
    val bodyLineHeightPx: Float = 0f,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "window must be positive: ${widthPx}x$heightPx" }
        require(density > 0f && charWidthPx > 0f) { "density and charWidthPx must be positive" }
        require(forcedColumns == null || forcedColumns in 1..MAX_COLUMNS) { "forcedColumns must be 1..2" }
    }
}

/**
 * Immutable result of the column math (LAY-04 to LAY-09, LAY-11). Equal geometries are interchangeable cache
 * keys (PAG-10). A separating vertical fold (LAY-10, LAY-12, LAY-13) reshapes the columns; [fold] and
 * [innerMargin] carry what LAY-14 needs to keep drawn items clear of the hinge.
 */
public data class Geometry(
    val windowWidth: Int,
    val windowHeight: Int,
    val minMeasure: Int,
    val targetMeasure: Int,
    val maxMeasure: Int,
    /** The `34 × charW` floor used when the user forces a column count (LAY-08). */
    val floorMeasure: Int,
    val baseGap: Int,
    val outerMargin: Int,
    val leftMargin: Int,
    val rightMargin: Int,
    val verticalMargin: Int,
    val columnCount: Int,
    val columnLefts: List<Int>,
    val columnWidths: List<Int>,
    /** Height available to text in one column. */
    val columnHeight: Int,
    val metrics: FlowMetrics,
    val typographyKey: Int,
    /** Distance between the columns: [baseGap] without a fold, the LAY-12 gutter with a separating vertical fold. */
    val gutter: Int = baseGap,
    /** `max(16dp, 1.5 × charW)`, plus 12dp per side in book posture (LAY-12, LAY-13). */
    val innerMargin: Int = 0,
    /** The fold that intersects the window, or null. LAY-14: nothing is drawn in its bounds plus [innerMargin]. */
    val fold: FoldSpec? = null,
    /** Posture derived from [fold] (POS-03, POS-04), explicit so it is visibly part of the cache key (PAG-10). */
    val posture: Posture = Posture.Standard,
    /**
     * Where text may be drawn, in window px (POS-04). The full window except in [Posture.Tabletop], where it is
     * the part above the fold top edge. [columnHeight] and [verticalMargin] are computed from its height.
     */
    val textRegion: PxRect = PxRect(0, 0, windowWidth, windowHeight),
    /** The control deck of tabletop (POS-04), below the hinge; null in every other posture. */
    val deckRegion: PxRect? = null,
)

/** Rectangle in window px, `right >= left` and `bottom >= top`. */
public data class PxRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left && bottom >= top) { "inverted rect" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Width of the narrowest column. */
internal val Geometry.narrowestColumn: Int get() = columnWidths.min()

/** True when the columns differ in width, which only a separating fold produces (LAY-11, LAY-12). */
internal val Geometry.hasUnequalColumns: Boolean get() = columnWidths.any { it != columnWidths[0] }

/**
 * Width a [slice] shown in [column] is laid out at: the column's own width, except that a block crossing columns
 * is laid out once at the narrowest column, so every break lands on a line boundary of the layout that follows.
 * [PageComposer] makes the same choice while paginating.
 */
internal fun Geometry.sliceWidth(
    column: Int,
    slice: Slice,
): Int =
    if (hasUnequalColumns && (slice.startLine > 0 || slice.continuesOnNextPage)) {
        narrowestColumn
    } else {
        columnWidths[column.coerceIn(0, columnWidths.lastIndex)]
    }

/** Whether the hinge line is vertical (book) or horizontal (tabletop). */
public enum class FoldOrientation { Vertical, Horizontal }

/** Whether the hinge hides pixels (`Full`) or not (`None`), as in the platform `OcclusionType`. */
public enum class FoldOcclusion { None, Full }

/**
 * A folding feature that may intersect the window (LAY-01). Bounds are in **window coordinates**, in px:
 * callers must translate the platform bounds (which are relative to the window) by the safe-drawing insets
 * applied to [WindowSpec.widthPx] and [WindowSpec.heightPx] before building this.
 */
public data class FoldSpec(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isSeparating: Boolean,
    val orientation: FoldOrientation,
    /** `HALF_OPENED` state. With a vertical orientation this is book posture (POS-03, LAY-13). */
    val halfOpened: Boolean,
    val occlusion: FoldOcclusion,
) {
    init {
        require(right >= left && bottom >= top) { "fold bounds must not be inverted" }
    }
}

internal fun FoldSpec.intersects(
    windowWidth: Int,
    windowHeight: Int,
): Boolean = right > 0 && left < windowWidth && bottom > 0 && top < windowHeight

private const val MIN_INNER_MARGIN_DP = 16f
private const val INNER_MARGIN_CHARS = 1.5f
private const val BOOK_EXTRA_DP = 12f
private const val MAX_COLUMNS = 2
private const val MIN_TABLETOP_LINES = 4f
private const val MIN_DECK_DP = 96f
private const val MAX_VERTICAL_MARGIN_DIVISOR = 8
private const val MIN_MEASURE_CHARS = 40f
private const val TARGET_MEASURE_CHARS = 66f
private const val MAX_MEASURE_CHARS = 75f
private const val FLOOR_MEASURE_CHARS = 28f
private const val FOLD_FLOOR_CHARS = 28f
private const val MIN_MARGIN_DP = 20f
private const val MARGIN_FRACTION = 0.06
private const val MIN_GAP_DP = 32f
private const val GAP_CHARS = 2.5f

/** Pure column and margin math (LAY-04 to LAY-09). */
public object GeometryCalculator {
    public fun compute(spec: WindowSpec): Geometry {
        val w = spec.widthPx
        val charW = spec.charWidthPx
        val minMeasure = (MIN_MEASURE_CHARS * charW).roundToInt()
        val maxMeasure = (MAX_MEASURE_CHARS * charW).roundToInt()
        val floorMeasure = (FLOOR_MEASURE_CHARS * charW).roundToInt()
        val baseGap = max((MIN_GAP_DP * spec.density).roundToInt(), (GAP_CHARS * charW).roundToInt())
        val baseMargin = max(MIN_MARGIN_DP * spec.density, (MARGIN_FRACTION * w).toFloat())
        val outer = (baseMargin * spec.marginScale).roundToInt()

        val auto = columnsFor(w, outer, baseGap, minMeasure)
        val cols =
            spec.forcedColumns?.let { minOf(it, columnsFor(w, outer, baseGap, floorMeasure)) } ?: auto

        val avail = max(w - 2 * outer, 1)
        val colWidth: Int
        val left: Int
        if (cols == 1) {
            colWidth = minOf(avail, maxMeasure)
            left = outer + (avail - colWidth) / 2
        } else {
            colWidth = (avail - (cols - 1) * baseGap) / cols
            left = outer + (avail - cols * colWidth - (cols - 1) * baseGap) / 2
        }
        val used = cols * colWidth + (cols - 1) * baseGap
        val fold = spec.fold?.takeIf { it.intersects(w, spec.heightPx) }
        val tabletopHeight = tabletopRegionHeight(fold, spec)
        val vertical = minOf(outer, (tabletopHeight ?: spec.heightPx) / MAX_VERTICAL_MARGIN_DIVISOR)
        val splitting = fold != null && fold.isSeparating && fold.orientation == FoldOrientation.Vertical
        val bookExtra = if (splitting && fold.halfOpened) (BOOK_EXTRA_DP * spec.density).roundToInt() else 0
        val innerBase =
            max((MIN_INNER_MARGIN_DP * spec.density).roundToInt(), (INNER_MARGIN_CHARS * charW).roundToInt())
        val innerMargin = innerBase + bookExtra
        val plain =
            Geometry(
                windowWidth = w,
                windowHeight = spec.heightPx,
                minMeasure = minMeasure,
                targetMeasure = (TARGET_MEASURE_CHARS * charW).roundToInt(),
                maxMeasure = maxMeasure,
                floorMeasure = floorMeasure,
                baseGap = baseGap,
                outerMargin = outer,
                leftMargin = left,
                rightMargin = w - left - used,
                verticalMargin = vertical,
                columnCount = cols,
                columnLefts = List(cols) { left + it * (colWidth + baseGap) },
                columnWidths = List(cols) { colWidth },
                columnHeight = max((tabletopHeight ?: spec.heightPx) - 2 * vertical, 1),
                metrics = spec.metrics,
                typographyKey = spec.typographyKey,
                gutter = baseGap,
                innerMargin = innerMargin,
                fold = fold,
                posture = if (tabletopHeight != null) Posture.Tabletop else postureOf(fold),
                textRegion = PxRect(0, 0, w, tabletopHeight ?: spec.heightPx),
                deckRegion = if (tabletopHeight != null) deckBelow(fold!!, w, spec.heightPx) else null,
            )
        return if (splitting) foldColumns(plain, fold, (FOLD_FLOOR_CHARS * charW).roundToInt()) else plain
    }

    /**
     * Height of the tabletop text region (the fold top edge, POS-04), or null when the posture is not tabletop, the
     * region would hold fewer than 4 body lines or the deck would be under 96dp, in which case the fold is ignored.
     */
    private fun tabletopRegionHeight(
        fold: FoldSpec?,
        spec: WindowSpec,
    ): Int? {
        if (fold == null || PostureClassifier.classify(fold) != Posture.Tabletop) return null
        val minRegion = max(ceil(MIN_TABLETOP_LINES * spec.bodyLineHeightPx).toInt(), 1)
        val minDeck = (MIN_DECK_DP * spec.density).roundToInt()
        return fold.top.takeIf { it >= minRegion && spec.heightPx - min(fold.bottom, spec.heightPx) >= minDeck }
    }

    private fun deckBelow(
        fold: FoldSpec,
        w: Int,
        h: Int,
    ) = PxRect(0, min(fold.bottom, h), w, h)

    /** Posture of a fold that does not reshape the text region: tabletop folds that fall back are Standard. */
    private fun postureOf(fold: FoldSpec?): Posture =
        PostureClassifier.classify(fold).takeIf { it != Posture.Tabletop } ?: Posture.Standard

    /**
     * Column placement around a separating vertical fold (LAY-10, LAY-12, LAY-14). The gutter is centered on
     * the fold bounds, so the two sides may differ in width. Two columns (one per side) when each side holds
     * the `28 × charW` fold floor, otherwise one column on the wider side, centered in the window and slid away
     * from the gutter only as far as needed to clear it. When no side has any room (a fold as
     * wide as the window) the plain geometry is kept, because there is nowhere to draw clear of the fold.
     */
    private fun foldColumns(
        plain: Geometry,
        fold: FoldSpec,
        floor: Int,
    ): Geometry {
        val foldWidth = fold.right - fold.left
        val gutter = max(plain.baseGap, foldWidth + 2 * plain.innerMargin)
        // floorDiv keeps both fold edges (plus innerMargin) outside the gutter for odd sums.
        val gutterLeft = Math.floorDiv(fold.left + fold.right - gutter, 2)
        val gutterRight = gutterLeft + gutter
        val leftRoom = gutterLeft - plain.outerMargin
        val rightRoom = plain.windowWidth - plain.outerMargin - gutterRight
        val lefts: List<Int>
        val widths: List<Int>
        if (leftRoom >= floor && rightRoom >= floor) {
            lefts = listOf(plain.outerMargin, gutterRight)
            widths = listOf(leftRoom, rightRoom)
        } else if (max(leftRoom, rightRoom) >= 1) {
            val onLeft = leftRoom >= rightRoom
            val room = if (onLeft) leftRoom else rightRoom
            val avail = max(plain.windowWidth - 2 * plain.outerMargin, 1)
            val width = minOf(room, plain.maxMeasure, avail).coerceAtLeast(1)
            // Centered in the window; when that would reach the gutter, slid just far enough to clear it.
            val lowest = if (onLeft) plain.outerMargin else max(plain.outerMargin, gutterRight)
            val highest = if (onLeft) gutterLeft - width else plain.windowWidth - plain.outerMargin - width
            val centered = (plain.windowWidth - width) / 2
            lefts = listOf(centered.coerceIn(lowest, max(lowest, highest)))
            widths = listOf(width)
        } else {
            return plain.copy(gutter = gutter)
        }
        val lastRight = lefts.last() + widths.last()
        return plain.copy(
            leftMargin = lefts.first(),
            rightMargin = plain.windowWidth - lastRight,
            columnCount = lefts.size,
            columnLefts = lefts,
            columnWidths = widths,
            gutter = gutter,
        )
    }

    /** `clamp(floor((W − 2·outer + gap) / (measure + gap)), 1, 3)` in exact integer math (LAY-07). */
    private fun columnsFor(
        w: Int,
        outer: Int,
        gap: Int,
        measure: Int,
    ): Int = Math.floorDiv(w - 2 * outer + gap, measure + gap).coerceIn(1, MAX_COLUMNS)
}
