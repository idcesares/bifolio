package dev.dcesares.bifolio.layout

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** LAY-04 to LAY-09, derived from the window only (LAY-01). Fixed seed; the seed is in every message. */
class GeometryTest {
    private fun spec(
        w: Int,
        h: Int = 2000,
        density: Float = 1f,
        charW: Float = 10f,
        scale: Float = 1f,
        forced: Int? = null,
    ) = WindowSpec(
        widthPx = w,
        heightPx = h,
        density = density,
        charWidthPx = charW,
        marginScale = scale,
        forcedColumns = forced,
    )

    @Test
    fun `LAY-04 measures are 40, 66 and 75 character widths`() {
        val g = GeometryCalculator.compute(spec(1000, charW = 10f))
        assertEquals(400, g.minMeasure)
        assertEquals(280, g.floorMeasure)
        assertEquals(660, g.targetMeasure)
        assertEquals(750, g.maxMeasure)
    }

    @Test
    fun `LAY-05 outer margin is max of 20dp and 6 percent, scaled by the user setting`() {
        // 6% of 1000 = 60 px beats 20dp = 20 px at density 1.
        assertEquals(60, GeometryCalculator.compute(spec(1000)).outerMargin)
        // 20dp at density 3 = 60 px beats 6% of 400 = 24.
        assertEquals(60, GeometryCalculator.compute(spec(400, density = 3f)).outerMargin)
        assertEquals(45, GeometryCalculator.compute(spec(1000, scale = 0.75f)).outerMargin)
        assertEquals(81, GeometryCalculator.compute(spec(1000, scale = 1.35f)).outerMargin)
    }

    @Test
    fun `LAY-06 base gap is max of 32dp and 2,5 character widths`() {
        assertEquals(32, GeometryCalculator.compute(spec(1000, charW = 10f)).baseGap)
        assertEquals(50, GeometryCalculator.compute(spec(1000, charW = 20f)).baseGap)
        assertEquals(96, GeometryCalculator.compute(spec(1000, density = 3f, charW = 10f)).baseGap)
    }

    @Test
    fun `LAY-07 auto columns follow the floor formula`() {
        // charW 10: min 400, gap 32. Outer 6% of W.
        assertEquals(1, GeometryCalculator.compute(spec(600)).columnCount)
        // W 1100: outer 66, (1100 - 132 + 32) / 432 = 2.31 -> 2
        assertEquals(2, GeometryCalculator.compute(spec(1100)).columnCount)
        // W 1400: outer 84, (1400 - 168 + 32) / 432 = 2.93 -> 2
        assertEquals(2, GeometryCalculator.compute(spec(1400)).columnCount)
        // W 1500: outer 90, (1500 - 180 + 32) / 432 = 3.13 -> 3
        assertEquals(3, GeometryCalculator.compute(spec(1500)).columnCount)
        // never more than 3
        assertEquals(3, GeometryCalculator.compute(spec(6000)).columnCount)
    }

    @Test
    fun `POS-06 a very wide window reaches 3 columns and never more`() {
        assertEquals(3, GeometryCalculator.compute(spec(2400)).columnCount)
        assertEquals(3, GeometryCalculator.compute(spec(6000)).columnCount)
    }

    @Test
    fun `POS-05 the smallest window still yields one usable column`() {
        // 320dp x 360dp at density 2.75 with 16 px characters.
        val g = GeometryCalculator.compute(spec(w = 880, h = 990, density = 2.75f, charW = 16f))
        assertEquals(1, g.columnCount)
        assertTrue(g.columnWidths.all { it > 0 })
    }

    @Test
    fun `LAY-08 forced columns are a maximum with a 28 character floor`() {
        // charW 10, gap 32, floor 280. W 1000: outer 60, (1000 - 120 + 32) / 312 = 2.92 -> 2.
        assertEquals(2, GeometryCalculator.compute(spec(1000, forced = 3)).columnCount)
        // W 1300: outer 78, auto 2, floor (1300 - 156 + 32) / 312 = 3.77 -> 3.
        assertEquals(3, GeometryCalculator.compute(spec(1300, forced = 3)).columnCount)
        assertEquals(2, GeometryCalculator.compute(spec(1300, forced = 2)).columnCount)
        assertEquals(1, GeometryCalculator.compute(spec(1300, forced = 1)).columnCount)
        // A forced count that cannot fit drops silently.
        assertEquals(1, GeometryCalculator.compute(spec(500, forced = 3)).columnCount)
    }

    @Test
    fun `LAY-09 a single column wider than the max measure is centered and the rest becomes margin`() {
        val g = GeometryCalculator.compute(spec(1000, forced = 1))
        assertEquals(1, g.columnCount)
        assertEquals(750, g.columnWidths[0])
        assertEquals(125, g.leftMargin)
        assertEquals(125, g.rightMargin)
        assertEquals(125, g.columnLefts[0])
    }

    @Test
    fun `LAY-11 columns without a fold are equal and separated by the base gap`() {
        val g = GeometryCalculator.compute(spec(1700))
        assertEquals(3, g.columnCount)
        assertEquals(1, g.columnWidths.toSet().size)
        for (i in 1 until g.columnCount) {
            assertEquals(g.baseGap, g.columnLefts[i] - g.columnLefts[i - 1] - g.columnWidths[i - 1])
        }
    }

    @Test
    fun `page height is the window height minus the vertical margins`() {
        val g = GeometryCalculator.compute(spec(1000, h = 1500))
        assertEquals(1500 - 2 * g.verticalMargin, g.columnHeight)
    }

    @Test
    fun `LAY-08 a forced count is honored down to the 28 character floor`() {
        // charW 10, gap 32, floor 280. W 800: outer 48, auto (800 - 96 + 32) / 432 = 1, floor (736) / 312 = 2.
        assertEquals(1, GeometryCalculator.compute(spec(800)).columnCount)
        assertEquals(2, GeometryCalculator.compute(spec(800, forced = 2)).columnCount)
        // 3 does not fit at 800, so it drops to the most that does.
        assertEquals(2, GeometryCalculator.compute(spec(800, forced = 3)).columnCount)
        // W 1100: outer 66, auto 2, floor (1100 - 132 + 32) / 312 = 3.2 -> 3.
        assertEquals(2, GeometryCalculator.compute(spec(1100)).columnCount)
        assertEquals(3, GeometryCalculator.compute(spec(1100, forced = 3)).columnCount)
        // A forced 1 is always honored.
        assertEquals(1, GeometryCalculator.compute(spec(1700, forced = 1)).columnCount)
    }

    @Test
    fun `property geometry invariants hold for any window`() {
        val rs = RandomSource.seeded(SEED)
        repeat(ITERATIONS) {
            val w = Arb.int(200..5000).next(rs)
            val h = Arb.int(300..4000).next(rs)
            val density = Arb.float(1f..4f).next(rs)
            val charW = Arb.float(6f..30f).next(rs)
            val scale = listOf(0.75f, 1f, 1.35f)[Arb.int(0..2).next(rs)]
            val forced = listOf(null, 1, 2, 3)[Arb.int(0..3).next(rs)]
            val s = WindowSpec(w, h, density, charW, scale, forced)
            val label = "seed=$SEED w=$w h=$h density=$density charW=$charW scale=$scale forced=$forced"
            val g = GeometryCalculator.compute(s)

            assertTrue(g.columnCount in 1..3, "$label cols=${g.columnCount}")
            if (forced != null) assertTrue(g.columnCount <= forced, "$label forced is a maximum")
            val used = g.columnWidths.sum() + (g.columnCount - 1) * g.baseGap
            assertEquals(w, g.leftMargin + used + g.rightMargin, "$label widths must add up to W")
            assertTrue(abs(g.leftMargin - g.rightMargin) <= 1 + g.columnCount, "$label margins symmetric")
            assertEquals(1, g.columnWidths.toSet().size, "$label equal widths")
            for (i in 0 until g.columnCount) {
                val expectedLeft = g.leftMargin + i * (g.columnWidths[0] + g.baseGap)
                assertEquals(expectedLeft, g.columnLefts[i], label)
            }
            if (g.columnCount > 1) {
                val floor = if (forced == null) g.minMeasure else minOf(g.minMeasure, g.floorMeasure)
                assertTrue(g.columnWidths[0] >= floor, "$label column ${g.columnWidths[0]} below floor $floor")
            } else {
                assertTrue(g.columnWidths[0] <= g.maxMeasure, "$label single column over max measure")
            }
            assertTrue(g.leftMargin >= 0 && g.rightMargin >= 0, label)
            assertEquals(h - 2 * g.verticalMargin, g.columnHeight, label)
        }
    }

    @Test
    fun `property auto column count never decreases when the window widens`() {
        val rs = RandomSource.seeded(SEED)
        repeat(ITERATIONS) {
            val density = Arb.float(1f..4f).next(rs)
            val charW = Arb.float(6f..30f).next(rs)
            var previous = 0
            var w = 200
            while (w <= 6000) {
                val cols = GeometryCalculator.compute(spec(w, density = density, charW = charW)).columnCount
                assertTrue(cols >= previous, "seed=$SEED density=$density charW=$charW w=$w: $cols < $previous")
                previous = cols
                w += 37
            }
        }
    }

    @Test
    fun `equal window gives equal geometry and different typography gives different geometry`() {
        val a = GeometryCalculator.compute(spec(1000))
        val b = GeometryCalculator.compute(spec(1000))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        val c = GeometryCalculator.compute(spec(1000).copy(typographyKey = 7))
        assertTrue(a != c)
    }

    private companion object {
        const val SEED = 20261017L
        const val ITERATIONS = 400
    }
}
