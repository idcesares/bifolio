package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.unit.IntOffset
import java.text.BreakIterator
import java.util.Locale

/**
 * One run of text the page paints, as far as selecting it is concerned (NAV-09). Offsets are indexes into [text];
 * only `[visibleStart, visibleEnd)` is on the page (a leaf split over two pages shows a slice of its lines), and
 * nothing outside it is ever selected or copied. Bounds are px from one fixed origin, the same for every run of a
 * spread, so a point can be compared with all of them.
 */
internal interface SelectableRun {
    val text: String
    val visibleStart: Int
    val visibleEnd: Int
    val left: Float
    val top: Float
    val right: Float
    val bottom: Float

    /** Ranges of [text] that are drawn but are not prose, such as footnote marker labels; left out of copied text. */
    val hidden: List<IntRange>

    /** The character offset nearest to the point, always inside the visible slice. */
    fun offsetAt(
        x: Float,
        y: Float,
    ): Int
}

/** A place in the text of a spread: which run, in reading order, and the offset inside it. */
internal data class TextPosition(
    val run: Int,
    val offset: Int,
) : Comparable<TextPosition> {
    override fun compareTo(other: TextPosition): Int = compareValuesBy(this, other, { it.run }, { it.offset })
}

/**
 * What is selected: two ends, in the order the reader dragged them. [start] and [end] are the same ends in reading
 * order, so dragging one handle past the other keeps working.
 */
internal class TextSelection(
    val anchor: TextPosition,
    val focus: TextPosition,
) {
    val start: TextPosition get() = if (anchor <= focus) anchor else focus
    val end: TextPosition get() = if (anchor <= focus) focus else anchor
    val isEmpty: Boolean get() = anchor == focus
}

/** Range of the selection inside a run, as `[start, end)`. */
internal typealias OffsetRange = Pair<Int, Int>

private val Words: ThreadLocal<BreakIterator> = ThreadLocal.withInitial { BreakIterator.getWordInstance(Locale.ROOT) }

/** The rules of selecting text on a page: what is under a finger, which word, which range, which text. Pure. */
internal object SelectionMath {
    /**
     * The position at ([x], [y]). Strict (a long press): null unless the point is on a run, so a press on empty page
     * selects nothing. Not strict (a drag): the nearest run, so the handle can go anywhere and stays on the text.
     */
    fun positionAt(
        runs: List<SelectableRun>,
        x: Float,
        y: Float,
        strict: Boolean,
    ): TextPosition? {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for ((index, run) in runs.withIndex()) {
            val distance = distanceTo(run, x, y, strict) ?: continue
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        if (best < 0) return null
        return TextPosition(best, runs[best].offsetAt(x, y))
    }

    /** How far the point is from [run], or null when the run does not count (empty, or off it when [strict]). */
    private fun distanceTo(
        run: SelectableRun,
        x: Float,
        y: Float,
        strict: Boolean,
    ): Float? {
        val dx = maxOf(run.left - x, 0f, x - run.right)
        val dy = maxOf(run.top - y, 0f, y - run.bottom)
        val skipped = run.visibleEnd <= run.visibleStart || (strict && (dx > 0f || dy > 0f))
        return if (skipped) null else dx + dy
    }

    /** The word at [position] as a selection, or null when the press was on blanks or punctuation. */
    fun wordAt(
        runs: List<SelectableRun>,
        position: TextPosition,
    ): TextSelection? {
        val run = runs.getOrNull(position.run) ?: return null
        val breaks = checkNotNull(Words.get())
        breaks.setText(run.text)
        // A press near the right edge of a word lands on the offset after it: try the character before as well.
        for (probe in intArrayOf(position.offset, position.offset - 1)) {
            val word = wordAround(run, breaks, probe) ?: continue
            val picked = TextSelection(TextPosition(position.run, word.first), TextPosition(position.run, word.last))
            // A footnote marker label is drawn but is not prose: pressing it selects nothing.
            if (textOf(runs, picked).isNotBlank()) return picked
        }
        return null
    }

    /** The visible part of the word that holds [probe], as `first..last` offsets, or null when it is not a word. */
    private fun wordAround(
        run: SelectableRun,
        breaks: BreakIterator,
        probe: Int,
    ): IntRange? {
        if (probe !in run.visibleStart until run.visibleEnd) return null
        val end = breaks.following(probe)
        val start = if (end == BreakIterator.DONE) BreakIterator.DONE else breaks.preceding(end)
        val isWord = start != BreakIterator.DONE && run.text.substring(start, end).any { it.isLetterOrDigit() }
        val from = maxOf(start, run.visibleStart)
        val to = minOf(end, run.visibleEnd)
        return if (isWord && to > from) from..to else null
    }

    /** The selection while a handle is dragged to ([x], [y]): the end that stays put and the one under the finger. */
    fun drag(
        fixed: TextPosition,
        runs: List<SelectableRun>,
        x: Float,
        y: Float,
    ): TextSelection? = positionAt(runs, x, y, strict = false)?.let { TextSelection(fixed, it) }

    /** The part of [run] (at index [index]) that [selection] covers, inside the visible slice, or null. */
    fun rangeIn(
        selection: TextSelection,
        index: Int,
        run: SelectableRun,
    ): OffsetRange? {
        if (index < selection.start.run || index > selection.end.run) return null
        val from = if (index == selection.start.run) selection.start.offset else run.visibleStart
        val to = if (index == selection.end.run) selection.end.offset else run.visibleEnd
        val start = from.coerceIn(run.visibleStart, run.visibleEnd)
        val end = to.coerceIn(start, run.visibleEnd)
        return if (end > start) start to end else null
    }

    /** The selected text: runs joined by a newline, marker labels and unseen text left out. */
    fun textOf(
        runs: List<SelectableRun>,
        selection: TextSelection,
    ): String {
        val parts = ArrayList<String>(1)
        for ((index, run) in runs.withIndex()) {
            val (from, to) = rangeIn(selection, index, run) ?: continue
            val piece = buildString { for (i in from until to) if (run.hidden.none { i in it }) append(run.text[i]) }
            if (piece.isNotEmpty()) parts.add(piece)
        }
        return parts.joinToString("\n")
    }
}

/**
 * Where the Copy and Share bar goes: above the first line of the selection ([top]) when it fits, otherwise below
 * the end ([bottom], which already includes the handle), centered on [centerX] and kept inside
 * `[regionLeft, regionRight]` (the window, or the half of a fold the selection starts in).
 */
@Suppress("LongParameterList")
internal fun placeSelectionToolbar(
    centerX: Int,
    top: Int,
    bottom: Int,
    width: Int,
    height: Int,
    windowHeight: Int,
    regionLeft: Int,
    regionRight: Int,
    margin: Int,
    gap: Int,
): IntOffset {
    val low = regionLeft + margin
    val x = (centerX - width / 2).coerceIn(low, maxOf(low, regionRight - margin - width))
    val above = top - gap - height
    val below = bottom + gap
    val y =
        when {
            above >= margin -> above
            below + height <= windowHeight - margin -> below
            else -> (windowHeight - margin - height).coerceAtLeast(margin)
        }
    return IntOffset(x, y)
}

/**
 * The horizontal span the Copy and Share bar may use: the whole window, or, beside a vertical separating fold, the
 * half where the selection starts, so the bar never straddles the hinge (LAY-12).
 */
internal fun selectionRegion(
    fold: dev.dcesares.bifolio.layout.FoldSpec?,
    x: Float,
    windowWidth: Int,
): IntRange =
    if (fold != null && fold.isSeparating && fold.orientation == dev.dcesares.bifolio.layout.FoldOrientation.Vertical) {
        if (x < fold.left) 0..fold.left else fold.right..windowWidth
    } else {
        0..windowWidth
    }
