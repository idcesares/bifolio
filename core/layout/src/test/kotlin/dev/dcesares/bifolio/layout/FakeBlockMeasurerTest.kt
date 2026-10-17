package dev.dcesares.bifolio.layout

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The fake must honor the [LineMetrics] contract, otherwise pagination tests prove nothing. */
class FakeBlockMeasurerTest {
    private val fake = FakeBlockMeasurer(charWidth = 10, lineHeight = 20)

    private fun assertContract(
        text: String,
        m: LineMetrics,
    ) {
        if (text.isEmpty()) {
            assertEquals(0, m.count)
            return
        }
        assertEquals(0, m.starts[0])
        assertEquals(text.length, m.ends[m.count - 1])
        for (i in 0 until m.count) {
            assertTrue(m.ends[i] > m.starts[i], "line $i is empty")
            assertTrue(m.bottoms[i] > m.tops[i])
            if (i + 1 < m.count) {
                assertEquals(m.ends[i], m.starts[i + 1], "lines must tile the text")
                assertTrue(m.tops[i + 1] >= m.bottoms[i])
            }
        }
    }

    @Test
    fun `wraps at spaces and keeps the space at the line end`() {
        val m = fake.measureLines("aaa bbb ccc", TextRole.BODY, 70)
        assertEquals(2, m.count)
        assertEquals(8, m.ends[0])
        assertEquals(40, m.height)
        assertContract("aaa bbb ccc", m)
    }

    @Test
    fun `hard breaks words longer than the width`() {
        val text = "x".repeat(25)
        val m = fake.measureLines(text, TextRole.BODY, 100)
        assertEquals(3, m.count)
        assertContract(text, m)
    }

    @Test
    fun `newline ends a line and empty text has no lines`() {
        val text = "ab\ncd\n\nef"
        val m = fake.measureLines(text, TextRole.CODE, 1000)
        assertEquals(4, m.count)
        assertContract(text, m)
        assertEquals(0, fake.measureLines("", TextRole.BODY, 100).count)
    }

    @Test
    fun `random texts and widths always satisfy the contract`() {
        val rs = RandomSource.seeded(SEED)
        val alphabet = "ab  \nxyz"
        repeat(500) {
            val len = Arb.int(0..200).next(rs)
            val text = String(CharArray(len) { alphabet[Arb.int(alphabet.indices).next(rs)] })
            val width = Arb.int(10..300).next(rs)
            assertContract(text, fake.measureLines(text, TextRole.BODY, width))
        }
    }

    private companion object {
        const val SEED = 20261017L
    }
}
