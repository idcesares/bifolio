package dev.dcesares.bifolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAR = 10f
private const val LINE = 20f

/**
 * A run on a grid: [perLine] characters of 10px per line, lines 20px tall, no word wrapping. [visibleLines] limits
 * the slice that is on the page, like a leaf split over two pages.
 */
private class GridRun(
    override val text: String,
    override val left: Float,
    override val top: Float,
    private val perLine: Int = 20,
    private val firstLine: Int = 0,
    private val lastLine: Int = Int.MAX_VALUE,
    override val hidden: List<IntRange> = emptyList(),
) : SelectableRun {
    private val lines = (text.length + perLine - 1) / perLine
    private val shownFrom = firstLine.coerceAtMost(lines - 1)
    private val shownTo = lastLine.coerceAtMost(lines - 1)

    override val visibleStart: Int get() = shownFrom * perLine
    override val visibleEnd: Int get() = minOf(text.length, (shownTo + 1) * perLine)
    override val right: Float get() = left + perLine * CHAR
    override val bottom: Float get() = top + (shownTo - shownFrom + 1) * LINE

    override fun offsetAt(
        x: Float,
        y: Float,
    ): Int {
        val line = ((y - top) / LINE).toInt().coerceIn(0, shownTo - shownFrom) + shownFrom
        val start = line * perLine
        val length = minOf(perLine, text.length - start)
        val column = Math.round((x - left) / CHAR).coerceIn(0, length)
        return (start + column).coerceIn(visibleStart, visibleEnd)
    }
}

/** NAV-09: selection range math on the text the page paints, no layout involved. */
class TextSelectionTest {
    private val paragraph = GridRun("The quick brown fox jumps over the lazy dog", left = 0f, top = 0f)
    private val second = GridRun("Second paragraph here.", left = 0f, top = 100f)
    private val runs: List<SelectableRun> = listOf(paragraph, second)

    @Test
    fun positionAtNeedsTheFingerOnTheTextWhenStrict() {
        assertEquals(TextPosition(0, 0), SelectionMath.positionAt(runs, 1f, 5f, strict = true))
        assertEquals(TextPosition(0, 25), SelectionMath.positionAt(runs, 50f, 30f, strict = true))
        // In the gap between the paragraphs, and past the right edge of the run: nothing under the finger.
        assertNull(SelectionMath.positionAt(runs, 50f, 70f, strict = true))
        assertNull(SelectionMath.positionAt(runs, 500f, 10f, strict = true))
        assertNull(SelectionMath.positionAt(emptyList(), 5f, 5f, strict = true))
    }

    @Test
    fun positionAtFollowsTheFingerWhenDragging() {
        // Below the last run: the end of it. Above the first: its start. Between: the nearest.
        assertEquals(TextPosition(1, 22), SelectionMath.positionAt(runs, 900f, 900f, strict = false))
        assertEquals(TextPosition(0, 0), SelectionMath.positionAt(runs, -50f, -50f, strict = false))
        assertEquals(TextPosition(0, 40), SelectionMath.positionAt(runs, 4f, 55f, strict = false))
        assertEquals(1, SelectionMath.positionAt(runs, 5f, 90f, strict = false)?.run)
    }

    @Test
    fun twoColumnsPickTheColumnUnderTheFinger() {
        val left = GridRun("left column text", left = 0f, top = 0f, perLine = 8)
        val right = GridRun("right column text", left = 300f, top = 0f, perLine = 8)
        val both = listOf<SelectableRun>(left, right)
        assertEquals(0, SelectionMath.positionAt(both, 20f, 10f, strict = false)?.run)
        assertEquals(1, SelectionMath.positionAt(both, 320f, 10f, strict = false)?.run)
        assertEquals(1, SelectionMath.positionAt(both, 290f, 10f, strict = false)?.run)
    }

    @Test
    fun longPressSelectsTheWordUnderTheFinger() {
        val quick = checkNotNull(SelectionMath.wordAt(runs, TextPosition(0, 6)))
        assertEquals("quick", SelectionMath.textOf(runs, quick))
        // At the right edge of a word the finger is nearer that word than the space after it.
        val brown = checkNotNull(SelectionMath.wordAt(runs, TextPosition(0, 15)))
        assertEquals("brown", SelectionMath.textOf(runs, brown))
        val first = checkNotNull(SelectionMath.wordAt(runs, TextPosition(0, 0)))
        assertEquals("The", SelectionMath.textOf(runs, first))
        val last = checkNotNull(SelectionMath.wordAt(runs, TextPosition(0, paragraph.text.length)))
        assertEquals("dog", SelectionMath.textOf(runs, last))
    }

    @Test
    fun aWordIsNeverBeyondTheVisibleSlice() {
        // 10 characters per line: "abcdefghij" "klmnopqrst", only the second line is on this page.
        val tail = GridRun("abcdefghijklmnopqrst", left = 0f, top = 0f, perLine = 10, firstLine = 1)
        val word = checkNotNull(SelectionMath.wordAt(listOf(tail), TextPosition(0, 12)))
        assertEquals(10, word.start.offset)
        assertEquals(20, word.end.offset)
        assertEquals("klmnopqrst", SelectionMath.textOf(listOf(tail), word))
    }

    @Test
    fun punctuationAndBlanksSelectNothing() {
        val sparse = GridRun("a    ...    b", left = 0f, top = 0f)
        assertNull(SelectionMath.wordAt(listOf(sparse), TextPosition(0, 6)))
    }

    @Test
    fun draggingPastTheFixedEndSwapsTheRange() {
        val fixed = TextPosition(0, 10)
        val after = checkNotNull(SelectionMath.drag(fixed, runs, 150f, 10f))
        assertEquals(TextPosition(0, 10), after.start)
        assertEquals(TextPosition(0, 15), after.end)
        val before = checkNotNull(SelectionMath.drag(fixed, runs, 30f, 10f))
        assertEquals(TextPosition(0, 3), before.start)
        assertEquals(TextPosition(0, 10), before.end)
        assertEquals("quick brown", SelectionMath.textOf(runs, TextSelection(TextPosition(0, 4), TextPosition(0, 15))))
        // The handle keeps being the same finger: both orders describe the same text.
        val reversed = TextSelection(TextPosition(0, 15), TextPosition(0, 4))
        assertEquals("quick brown", SelectionMath.textOf(runs, reversed))
    }

    @Test
    fun selectionAcrossRunsJoinsThemWithNewlines() {
        val across = TextSelection(TextPosition(0, 40), TextPosition(1, 6))
        assertEquals("dog\nSecond", SelectionMath.textOf(runs, across))
    }

    @Test
    fun selectionNeverIncludesWhatIsNotOnThePage() {
        val partial = GridRun("abcdefghijklmnopqrst", left = 0f, top = 0f, perLine = 10, lastLine = 0)
        val all = TextSelection(TextPosition(0, 0), TextPosition(0, 20))
        assertEquals("abcdefghij", SelectionMath.textOf(listOf(partial), all))
        assertEquals(0 to 10, SelectionMath.rangeIn(all, 0, partial))
    }

    @Test
    fun footnoteMarkerLabelsAreLeftOutOfTheCopiedText() {
        val noted = GridRun("Claim.1 More text", left = 0f, top = 0f, hidden = listOf(6..6))
        val selection = TextSelection(TextPosition(0, 0), TextPosition(0, 17))
        assertEquals("Claim. More text", SelectionMath.textOf(listOf(noted), selection))
    }

    @Test
    fun pressingAFootnoteMarkerLabelSelectsNothing() {
        val noted = GridRun("Claim 1 More text", left = 0f, top = 0f, hidden = listOf(6..6))
        assertNull(SelectionMath.wordAt(listOf(noted), TextPosition(0, 6)))
        assertNull(SelectionMath.wordAt(listOf(noted), TextPosition(0, 7)))
    }

    @Test
    fun emptySelectionIsEmptyAndCopiesNothing() {
        val caret = TextSelection(TextPosition(0, 4), TextPosition(0, 4))
        assertTrue(caret.isEmpty)
        assertEquals("", SelectionMath.textOf(runs, caret))
    }

    @Test
    fun rangeInIsLimitedToTheRunsTheSelectionTouches() {
        val across = TextSelection(TextPosition(0, 40), TextPosition(1, 6))
        assertEquals(40 to paragraph.text.length, SelectionMath.rangeIn(across, 0, paragraph))
        assertEquals(0 to 6, SelectionMath.rangeIn(across, 1, second))
        val inside = TextSelection(TextPosition(1, 2), TextPosition(1, 5))
        assertNull(SelectionMath.rangeIn(inside, 0, paragraph))
        assertNotNull(SelectionMath.rangeIn(inside, 1, second))
    }

    @Test
    fun toolbarGoesAboveTheFirstLineAndStaysInsideTheRegion() {
        // Room above: sits above the selection start, centered on it.
        val above =
            placeSelectionToolbar(
                centerX = 300,
                top = 400,
                bottom = 460,
                width = 200,
                height = 60,
                windowHeight = 1000,
                regionLeft = 0,
                regionRight = 600,
                margin = 16,
                gap = 8,
            )
        assertEquals(200, above.x)
        assertEquals(332, above.y)
        // No room above: below the end of the selection.
        val below =
            placeSelectionToolbar(
                centerX = 300,
                top = 20,
                bottom = 80,
                width = 200,
                height = 60,
                windowHeight = 1000,
                regionLeft = 0,
                regionRight = 600,
                margin = 16,
                gap = 8,
            )
        assertEquals(88, below.y)
        // Near the fold: kept inside its half.
        val edge =
            placeSelectionToolbar(
                centerX = 580,
                top = 400,
                bottom = 460,
                width = 200,
                height = 60,
                windowHeight = 1000,
                regionLeft = 0,
                regionRight = 600,
                margin = 16,
                gap = 8,
            )
        assertEquals(384, edge.x)
        val start =
            placeSelectionToolbar(
                centerX = 5,
                top = 400,
                bottom = 460,
                width = 200,
                height = 60,
                windowHeight = 1000,
                regionLeft = 100,
                regionRight = 600,
                margin = 16,
                gap = 8,
            )
        assertEquals(116, start.x)
    }

    @Test
    fun theBarStaysInTheHalfOfTheFoldWhereTheSelectionStarts() {
        val hinge =
            dev.dcesares.bifolio.layout.FoldSpec(
                left = 590,
                top = 0,
                right = 610,
                bottom = 900,
                isSeparating = true,
                orientation = dev.dcesares.bifolio.layout.FoldOrientation.Vertical,
                halfOpened = false,
                occlusion = dev.dcesares.bifolio.layout.FoldOcclusion.Full,
            )
        assertEquals(0..590, selectionRegion(hinge, 100f, 1200))
        assertEquals(610..1200, selectionRegion(hinge, 900f, 1200))
        assertEquals(0..1200, selectionRegion(null, 900f, 1200))
        assertEquals(0..1200, selectionRegion(hinge.copy(isSeparating = false), 900f, 1200))
    }
}
