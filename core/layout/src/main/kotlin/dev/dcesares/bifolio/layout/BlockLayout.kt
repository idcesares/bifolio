package dev.dcesares.bifolio.layout

/** Unit table of a [BlockLayout]: for each line its unit, and for each unit its first line, length and keep flag. */
internal class Units(
    val of: IntArray,
    val first: IntArray,
    val length: IntArray,
    val keep: BooleanArray,
)

/**
 * A top-level block flattened into lines at one column width. Everything the paginator needs, as parallel
 * arrays so the per-line loops allocate nothing. Immutable once built.
 *
 * Lines are grouped in **units**: the indivisible-by-rule groups of PAG-02. A paragraph, list item or quote
 * paragraph is one unit; every line of code and every table row is its own unit; a heading, a rule and an
 * atomic block is one unit that must stay whole ([unitKeep]).
 */
@Suppress("LongParameterList") // Parallel arrays of one immutable value; grouping them would only add indirection.
internal class BlockLayout(
    /** Line top and bottom in px from the top of the block. Nondecreasing. */
    val tops: IntArray,
    val bottoms: IntArray,
    /** Source offsets of each line relative to the start of the block range (ADR-0005). */
    val srcStart: IntArray,
    val srcEnd: IntArray,
    /** Rendered characters on each line, used to prove no text is lost or duplicated. */
    val chars: IntArray,
    val units: Units,
    /** Leading table rows repeated on continuation pages (0 or 1). */
    val headerLines: Int,
    val headerHeight: Int,
    /** True for headings (PAG-03). */
    val keepWithNext: Boolean,
    /** What to draw for each line (indent, text leaf, decorations). */
    val draw: LineDraws,
) {
    val lineCount: Int get() = tops.size

    /** Height in px of lines `[from, to)`. */
    fun height(
        from: Int,
        to: Int,
    ): Int = bottoms[to - 1] - tops[from]

    /**
     * Whether a page may end before line [b] when the slice starts at [s] (PAG-02, PAG-06, PAG-08):
     * a break between units is free; inside a unit of four lines or more both sides keep at least two lines;
     * units of three lines or fewer and unitKeep units never split; a table keeps its header with one row.
     */
    fun allowsBreak(
        s: Int,
        b: Int,
    ): Boolean {
        if (headerLines > 0 && s == 0 && b <= headerLines) return false
        val u = units.of[b]
        val first = units.first[u]
        if (first == b) return true
        val len = units.length[u]
        if (units.keep[u] || len <= MAX_UNSPLITTABLE_LINES) return false
        return b - maxOf(first, s) >= MIN_SIDE_LINES && first + len - b >= MIN_SIDE_LINES
    }

    /** Fewest lines from the top that satisfy "at least 2 lines" of PAG-03 without breaking PAG-02. */
    fun leadLines(): Int {
        var e = minOf(MIN_SIDE_LINES, lineCount)
        while (e < lineCount && !allowsBreak(0, e)) e++
        return e
    }

    /** Index of the last line whose start is at or before [offset] (block-relative source offset). */
    fun lineAtOffset(offset: Int): Int {
        var lo = 0
        var hi = lineCount - 1
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (srcStart[mid] <= offset) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    companion object {
        const val MAX_UNSPLITTABLE_LINES = 3
        const val MIN_SIDE_LINES = 2
    }
}

/** Growable accumulator for [BlockLayout]. Not thread-safe. */
internal class BlockLayoutBuilder {
    private var tops = IntArray(INITIAL)
    private var bottoms = IntArray(INITIAL)
    private var srcStart = IntArray(INITIAL)
    private var srcEnd = IntArray(INITIAL)
    private var chars = IntArray(INITIAL)
    private var unitOf = IntArray(INITIAL)
    private var unitFirst = IntArray(INITIAL)
    private var unitLength = IntArray(INITIAL)
    private var unitKeep = BooleanArray(INITIAL)
    private var kinds = IntArray(INITIAL)
    private var xs = IntArray(INITIAL)
    private var ws = IntArray(INITIAL)
    private var leaves = arrayOfNulls<TextLeaf>(INITIAL)
    private var leafTops = IntArray(INITIAL)
    private var leafBottoms = IntArray(INITIAL)
    private var tables = arrayOfNulls<TableDraw>(INITIAL)
    private var callouts = IntArray(INITIAL)
    private val markers = ArrayList<MarkerRec>()
    private val bars = ArrayList<BarRec>()
    var lineCount = 0
        private set
    private var unitCount = 0

    /** Bottom of the last line so far. */
    var y = 0
        private set
    var headerLines = 0
    var headerHeight = 0

    val isEmpty: Boolean get() = lineCount == 0

    private var pendingGap = 0

    /** Requests [px] of space before the next run; ignored before the first line. */
    fun gap(px: Int) {
        pendingGap = px
    }

    /** Starts a run of lines (a paragraph, a rule, a table...), applying the pending gap. */
    fun beginRun() {
        if (lineCount > 0) y += pendingGap
        pendingGap = 0
    }

    fun beginUnit(keep: Boolean) {
        if (unitCount == unitFirst.size) {
            unitFirst = unitFirst.copyOf(unitCount * 2)
            unitLength = unitLength.copyOf(unitCount * 2)
            unitKeep = unitKeep.copyOf(unitCount * 2)
        }
        unitFirst[unitCount] = lineCount
        unitLength[unitCount] = 0
        unitKeep[unitCount] = keep
        unitCount++
    }

    /** Appends a line to the current unit. [top] and [bottom] are relative to the current [y]. */
    fun line(
        top: Int,
        bottom: Int,
        src0: Int,
        src1: Int,
        charCount: Int,
    ) {
        if (lineCount == tops.size) grow()
        tops[lineCount] = y + top
        bottoms[lineCount] = y + bottom
        srcStart[lineCount] = src0
        srcEnd[lineCount] = maxOf(src0, src1)
        chars[lineCount] = charCount
        unitOf[lineCount] = unitCount - 1
        unitLength[unitCount - 1]++
        lineCount++
    }

    /** Describes how the line just added is drawn. */
    fun draw(
        kind: Int,
        x: Int,
        width: Int,
        leaf: TextLeaf? = null,
        leafTop: Int = 0,
        leafBottom: Int = 0,
        table: TableDraw? = null,
        callout: Int = 0,
    ) {
        val i = lineCount - 1
        kinds[i] = kind
        xs[i] = x
        ws[i] = width
        leaves[i] = leaf
        leafTops[i] = leafTop
        leafBottoms[i] = leafBottom
        tables[i] = table
        callouts[i] = callout
    }

    fun marker(rec: MarkerRec) {
        markers.add(rec)
    }

    fun bar(rec: BarRec) {
        bars.add(rec)
    }

    /** Moves [y] below the lines just added; [consumed] is the run height measured from [y]. */
    fun advance(consumed: Int) {
        y += consumed
    }

    private fun grow() {
        val n = lineCount * 2
        tops = tops.copyOf(n)
        bottoms = bottoms.copyOf(n)
        srcStart = srcStart.copyOf(n)
        srcEnd = srcEnd.copyOf(n)
        chars = chars.copyOf(n)
        unitOf = unitOf.copyOf(n)
        kinds = kinds.copyOf(n)
        xs = xs.copyOf(n)
        ws = ws.copyOf(n)
        leaves = leaves.copyOf(n)
        leafTops = leafTops.copyOf(n)
        leafBottoms = leafBottoms.copyOf(n)
        tables = tables.copyOf(n)
        callouts = callouts.copyOf(n)
    }

    fun build(keepWithNext: Boolean): BlockLayout =
        BlockLayout(
            tops = tops.copyOf(lineCount),
            bottoms = bottoms.copyOf(lineCount),
            srcStart = srcStart.copyOf(lineCount),
            srcEnd = srcEnd.copyOf(lineCount),
            chars = chars.copyOf(lineCount),
            units =
                Units(
                    unitOf.copyOf(lineCount),
                    unitFirst.copyOf(unitCount),
                    unitLength.copyOf(unitCount),
                    unitKeep.copyOf(unitCount),
                ),
            headerLines = headerLines,
            headerHeight = headerHeight,
            keepWithNext = keepWithNext,
            draw =
                LineDraws(
                    kinds.copyOf(lineCount),
                    xs.copyOf(lineCount),
                    ws.copyOf(lineCount),
                    leaves.copyOf(lineCount),
                    leafTops.copyOf(lineCount),
                    leafBottoms.copyOf(lineCount),
                    tables.copyOf(lineCount),
                    callouts.copyOf(lineCount),
                    markers.toList(),
                    bars.toList(),
                ),
        )

    private companion object {
        const val INITIAL = 16
    }
}

/** What a flattened line is, for drawing. */
internal object LineKind {
    const val TEXT = 0
    const val CODE = 1
    const val RULE = 2
    const val ATOMIC = 3
    const val TABLE_ROW = 4
    const val TITLE = 5
}

internal class MarkerRec(
    val line: Int,
    val x: Int,
    val width: Int,
    val kind: MarkerKind,
    val number: Int,
)

internal class BarRec(
    val firstLine: Int,
    val lastLine: Int,
    val x: Int,
    val kind: BarKind,
)

internal class TableDraw(
    val cells: List<DrawCell>,
    val totalWidth: Int,
    val header: Boolean,
    /** Width the table may use before it has to scroll. */
    val available: Int,
)

/** Per-line drawing data of a [BlockLayout], parallel to its line arrays. */
@Suppress("LongParameterList") // Parallel per-line arrays, same shape as BlockLayout.
internal class LineDraws(
    val kind: IntArray,
    val x: IntArray,
    val width: IntArray,
    val leaf: Array<TextLeaf?>,
    /** Top and bottom of the line inside its leaf, in px. */
    val leafTop: IntArray,
    val leafBottom: IntArray,
    val table: Array<TableDraw?>,
    /** Ordinal of the callout kind for [LineKind.TITLE] lines. */
    val callout: IntArray,
    val markers: List<MarkerRec>,
    val bars: List<BarRec>,
)
