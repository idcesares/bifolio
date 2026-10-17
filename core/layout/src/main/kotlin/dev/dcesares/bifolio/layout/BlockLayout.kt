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
        )

    private companion object {
        const val INITIAL = 16
    }
}
