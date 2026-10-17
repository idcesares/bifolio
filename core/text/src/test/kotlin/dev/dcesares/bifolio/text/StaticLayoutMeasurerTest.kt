package dev.dcesares.bifolio.text

import dev.dcesares.bifolio.layout.LineMetrics
import dev.dcesares.bifolio.layout.TextRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** OPN-05, TYP-04..06: the StaticLayout measurer keeps the BlockMeasurer contract of core:layout. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StaticLayoutMeasurerTest {
    private val measurer = StaticLayoutBlockMeasurer(TextStyleConfig())

    private val prose =
        "The quick brown fox jumps over the lazy dog and keeps running through the quiet forest " +
            "until the evening comes and the lamps are lit in every window of the village below."

    private fun assertContract(
        text: String,
        m: LineMetrics,
    ) {
        if (text.isEmpty()) {
            assertEquals(0, m.count)
            return
        }
        assertTrue(m.count > 0)
        assertEquals(0, m.starts[0])
        assertEquals(text.length, m.ends[m.count - 1])
        for (i in 0 until m.count) {
            assertTrue("empty line $i", m.ends[i] > m.starts[i])
            assertTrue("height line $i", m.tops[i] < m.bottoms[i])
            if (i + 1 < m.count) {
                assertEquals("tiling at $i", m.ends[i], m.starts[i + 1])
                assertTrue("overlap at $i", m.tops[i + 1] >= m.bottoms[i])
            }
        }
    }

    @Test
    fun `empty text has zero lines`() {
        assertContract("", measurer.measureLines("", TextRole.BODY, 400))
    }

    @Test
    fun `narrow width breaks prose into several lines that tile the text`() {
        val wide = measurer.measureLines(prose, TextRole.BODY, 2000)
        val narrow = measurer.measureLines(prose, TextRole.BODY, 300)
        assertContract(prose, wide)
        assertContract(prose, narrow)
        assertTrue("narrow ${narrow.count} vs wide ${wide.count}", narrow.count > wide.count)
    }

    @Test
    fun `a hard line break stays inside its line and a final one adds no empty line`() {
        val text = "one\ntwo\n"
        val m = measurer.measureLines(text, TextRole.BODY, 2000)
        assertContract(text, m)
        assertEquals(2, m.count)
        assertEquals(4, m.ends[0])
    }

    @Test
    fun `blank lines in the middle are kept as lines`() {
        val text = "a\n\nb"
        val m = measurer.measureLines(text, TextRole.CODE, 2000)
        assertContract(text, m)
        assertEquals(3, m.count)
    }

    @Test
    fun `every role satisfies the contract at several widths`() {
        for (role in TextRole.entries) {
            for (w in listOf(1, 40, 120, 500, 3000)) {
                assertContract(prose, measurer.measureLines(prose, role, w))
            }
        }
    }

    @Test
    fun `headings are taller than body lines`() {
        val body = measurer.measureLines("Title", TextRole.BODY, 2000)
        val h1 = measurer.measureLines("Title", TextRole.HEADING_1, 2000)
        assertTrue(h1.bottoms[0] - h1.tops[0] > body.bottoms[0] - body.tops[0])
    }

    @Test
    fun `surrogate pairs are never split at a width of one pixel`() {
        val text = "😀😀😀"
        val m = measurer.measureLines(text, TextRole.BODY, 1)
        assertContract(text, m)
        for (i in 0 until m.count) assertTrue(!Character.isLowSurrogate(text[m.starts[i]]) || m.starts[i] == 0)
    }

    @Test
    fun `intrinsic width is the longest hard line and grows with text`() {
        val short = measurer.intrinsicWidth("ab", TextRole.TABLE_CELL)
        val long = measurer.intrinsicWidth("ab\nabcdefghijkl", TextRole.TABLE_CELL)
        assertTrue(short > 0)
        assertTrue(long > short)
        assertEquals(0, measurer.intrinsicWidth("", TextRole.BODY))
    }

    @Test
    fun `measuring from many threads gives the same result as from one`() {
        val expected = measurer.measureLines(prose, TextRole.BODY, 300)
        val results = java.util.concurrent.ConcurrentLinkedQueue<LineMetrics>()
        val work = { repeat(20) { results.add(measurer.measureLines(prose, TextRole.BODY, 300)) } }
        val threads = List(8) { Thread(work) }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(160, results.size)
        for (r in results) {
            assertEquals(expected.count, r.count)
            assertTrue(expected.ends.copyOf(expected.count).contentEquals(r.ends.copyOf(r.count)))
        }
    }

    @Test
    fun `justification needs hyphenation and a measure of at least 45 character widths`() {
        val on = TextStyleConfig(justify = true, hyphenate = true, charWidthPx = 10f)
        assertTrue(StaticLayoutBlockMeasurer.justifies(on, 450))
        assertTrue(!StaticLayoutBlockMeasurer.justifies(on, 449))
        assertTrue(!StaticLayoutBlockMeasurer.justifies(on.copy(hyphenate = false), 900))
        assertTrue(!StaticLayoutBlockMeasurer.justifies(on.copy(justify = false), 900))
    }

    @Test
    fun `columns narrower than 45 character widths use the balanced strategy`() {
        val c = TextStyleConfig(charWidthPx = 10f)
        assertTrue(StaticLayoutBlockMeasurer.balanced(c, TextRole.BODY, 449))
        assertTrue(!StaticLayoutBlockMeasurer.balanced(c, TextRole.BODY, 450))
        assertTrue(StaticLayoutBlockMeasurer.balanced(c, TextRole.HEADING_2, 2000))
    }
}
