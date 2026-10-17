package dev.dcesares.bifolio.layout

import kotlin.math.max
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
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "window must be positive: ${widthPx}x$heightPx" }
        require(density > 0f && charWidthPx > 0f) { "density and charWidthPx must be positive" }
        require(forcedColumns == null || forcedColumns in 1..MAX_COLUMNS) { "forcedColumns must be 1..3" }
    }
}

/**
 * Immutable result of the column math (LAY-04 to LAY-09, LAY-11). Equal geometries are interchangeable cache
 * keys (PAG-10). Folds (LAY-10, LAY-12 to LAY-14) are added with the posture work and extend this type.
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
)

private const val MAX_COLUMNS = 3
private const val MAX_VERTICAL_MARGIN_DIVISOR = 8
private const val MIN_MEASURE_CHARS = 45f
private const val TARGET_MEASURE_CHARS = 66f
private const val MAX_MEASURE_CHARS = 75f
private const val FLOOR_MEASURE_CHARS = 34f
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
        val vertical = minOf(outer, spec.heightPx / MAX_VERTICAL_MARGIN_DIVISOR)
        return Geometry(
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
            columnHeight = max(spec.heightPx - 2 * vertical, 1),
            metrics = spec.metrics,
            typographyKey = spec.typographyKey,
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
