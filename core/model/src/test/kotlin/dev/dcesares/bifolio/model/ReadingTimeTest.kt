package dev.dcesares.bifolio.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingTimeTest {
    private val source = "# One\n\nalpha beta gamma\n\n## Two\n\ndelta epsilon\n\n# Three\n\nzeta\n"

    private fun doc(): Document =
        Document(
            title = "One",
            source = source,
            blocks =
                listOf(
                    Heading(1, listOf(Inline.Text("One")), "one", SourceRange(0, 5)),
                    Paragraph(listOf(Inline.Text("alpha beta gamma")), SourceRange(7, 23)),
                    Heading(2, listOf(Inline.Text("Two")), "two", SourceRange(25, 31)),
                    Paragraph(listOf(Inline.Text("delta epsilon")), SourceRange(33, 46)),
                    Heading(1, listOf(Inline.Text("Three")), "three", SourceRange(48, 55)),
                    Paragraph(listOf(Inline.Text("zeta")), SourceRange(57, 61)),
                ),
        )

    @Test
    fun `UI-06 words remaining in the document count from the anchor`() {
        val words = WordIndex(doc())
        assertEquals(9, words.remaining(Anchor(0, 0)).inDocument)
        assertEquals(4, words.remaining(Anchor(3, 0)).inDocument)
        assertEquals(0, words.remaining(Anchor(5, 4)).inDocument)
    }

    @Test
    fun `UI-06 the section ends at the next heading of the same or a higher level`() {
        val words = WordIndex(doc())
        assertEquals(6, words.remaining(Anchor(1, 0)).inSection)
        assertEquals(2, words.remaining(Anchor(3, 0)).inSection)
        assertEquals(1, words.remaining(Anchor(5, 0)).inSection)
    }

    @Test
    fun `UI-06 default speed is 230 words per minute`() {
        val pace = ReadingPace()
        assertEquals(230, pace.wordsPerMinute)
        assertEquals(1, pace.minutesFor(230))
        assertEquals(2, pace.minutesFor(231))
        assertEquals(0, pace.minutesFor(0))
    }

    @Test
    fun `UI-06 speed calibrates after ten turns and stays between 120 and 450`() {
        val pace = ReadingPace()
        repeat(9) { pace.recordTurn(words = 300, millis = 60_000) }
        assertEquals(230, pace.wordsPerMinute)
        pace.recordTurn(words = 300, millis = 60_000)
        assertEquals(300, pace.wordsPerMinute)

        val slow = ReadingPace()
        repeat(10) { slow.recordTurn(words = 30, millis = 60_000) }
        assertEquals(120, slow.wordsPerMinute)

        val fast = ReadingPace()
        repeat(10) { fast.recordTurn(words = 600, millis = 60_000) }
        assertEquals(450, fast.wordsPerMinute)
    }

    @Test
    fun `UI-06 pauses and skimmed jumps do not count as reading`() {
        val pace = ReadingPace()
        repeat(10) { pace.recordTurn(words = 300, millis = 10 * 60_000L) }
        repeat(10) { pace.recordTurn(words = 5_000, millis = 1_000) }
        assertEquals(230, pace.wordsPerMinute)
    }
}
