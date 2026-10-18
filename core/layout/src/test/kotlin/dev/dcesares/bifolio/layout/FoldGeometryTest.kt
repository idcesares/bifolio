package dev.dcesares.bifolio.layout

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** LAY-10, LAY-12, LAY-13, LAY-14 (separating folds only), POS-03 (model). Fixed seed, shown in messages. */
class FoldGeometryTest {
    private fun vFold(
        left: Int,
        width: Int,
        h: Int,
        separating: Boolean = true,
        halfOpened: Boolean = false,
        occlusion: FoldOcclusion = FoldOcclusion.None,
    ) = FoldSpec(
        left = left,
        top = 0,
        right = left + width,
        bottom = h,
        isSeparating = separating,
        orientation = FoldOrientation.Vertical,
        halfOpened = halfOpened,
        occlusion = occlusion,
    )

    private fun spec(
        w: Int,
        h: Int = 2000,
        density: Float = 1f,
        charW: Float = 10f,
        forced: Int? = null,
        fold: FoldSpec? = null,
    ) = WindowSpec(
        widthPx = w,
        heightPx = h,
        density = density,
        charWidthPx = charW,
        forcedColumns = forced,
        fold = fold,
    )

    @Test
    fun `LAY-12 gutter is centered on the fold and follows the formula`() {
        // charW 10, density 1: innerMargin = max(16, 15) = 16; baseGap 32.
        val flat = GeometryCalculator.compute(spec(1600, fold = vFold(790, 20, 2000, occlusion = FoldOcclusion.Full)))
        assertEquals(2, flat.columnCount)
        assertEquals(16, flat.innerMargin)
        assertEquals(20 + 32, flat.gutter) // max(32, 20 + 2*16)
        val gap = flat.columnLefts[1] - flat.columnLefts[0] - flat.columnWidths[0]
        assertEquals(flat.gutter, gap)
        assertEquals(800, flat.columnLefts[0] + flat.columnWidths[0] + gap / 2)
    }

    @Test
    fun `LAY-12 the fold width is the reported bounds width for either occlusion`() {
        val zero = GeometryCalculator.compute(spec(1600, fold = vFold(800, 0, 2000)))
        assertEquals(32, zero.gutter) // max(32, 0 + 2*16)
        val none = GeometryCalculator.compute(spec(1600, fold = vFold(790, 40, 2000)))
        val full = GeometryCalculator.compute(spec(1600, fold = vFold(790, 40, 2000, occlusion = FoldOcclusion.Full)))
        assertEquals(72, none.gutter) // max(32, 40 + 2*16)
        assertEquals(none.columnLefts, full.columnLefts)
    }

    @Test
    fun `LAY-13 book posture adds 24dp to the gutter, spec example`() {
        // charW 9, density 1 so dp = px: innerMargin = max(16, 13.5) = 16.
        val flat = GeometryCalculator.compute(spec(1600, charW = 9f, fold = vFold(800, 0, 2000)))
        val book = GeometryCalculator.compute(spec(1600, charW = 9f, fold = vFold(800, 0, 2000, halfOpened = true)))
        assertEquals(32, flat.gutter)
        assertEquals(56, book.gutter)
        assertEquals(16, flat.innerMargin)
        assertEquals(28, book.innerMargin)
    }

    @Test
    fun `LAY-13 book posture scales 12dp with density`() {
        val book = GeometryCalculator.compute(spec(2400, density = 2f, fold = vFold(1200, 0, 2000, halfOpened = true)))
        assertEquals(32 + 24, book.innerMargin) // 16dp + 12dp at density 2
    }

    @Test
    fun `LAY-10 fold rule wins over a forced single column when each side fits the floor`() {
        // charW 10: floor 340. Window 1000, outer 60, gutter 32: sides 60..484 and 516..940 = 424 each.
        val g = GeometryCalculator.compute(spec(1000, forced = 1, fold = vFold(500, 0, 2000)))
        assertEquals(2, g.columnCount)
    }

    @Test
    fun `LAY-10 three columns round down to two`() {
        val g = GeometryCalculator.compute(spec(1700, forced = 3, fold = vFold(850, 0, 2000)))
        assertEquals(2, g.columnCount)
        val auto = GeometryCalculator.compute(spec(6000, fold = vFold(3000, 0, 2000)))
        assertEquals(2, auto.columnCount)
    }

    @Test
    fun `LAY-10 falls back to one column on the wider side when two cannot fit`() {
        // Window 600, outer 36, gutter 32. Fold at 400: left 36..384 = 348 (floor 340 fits), right 416..564 = 148 no.
        val g = GeometryCalculator.compute(spec(600, fold = vFold(400, 0, 2000)))
        assertEquals(1, g.columnCount)
        assertTrue(g.columnLefts[0] >= 36 && g.columnLefts[0] + g.columnWidths[0] <= 400 - g.innerMargin)
        // Mirror: the wider side is on the right.
        val m = GeometryCalculator.compute(spec(600, fold = vFold(200, 0, 2000)))
        assertEquals(1, m.columnCount)
        assertTrue(m.columnLefts[0] >= 200 + m.innerMargin)
    }

    @Test
    fun `LAY-12 an off-centre fold gives unequal side widths and the gutter still tracks the hinge`() {
        val g = GeometryCalculator.compute(spec(1800, fold = vFold(1000, 0, 2000)))
        assertEquals(2, g.columnCount)
        assertTrue(g.columnWidths[0] != g.columnWidths[1])
        val gap = g.columnLefts[1] - g.columnLefts[0] - g.columnWidths[0]
        assertEquals(g.gutter, gap)
        assertTrue(abs(g.columnLefts[0] + g.columnWidths[0] + gap / 2 - 1000) <= 1)
    }

    @Test
    fun `a fold that does not intersect the window is dropped`() {
        val plain = GeometryCalculator.compute(spec(1600))
        assertEquals(plain, GeometryCalculator.compute(spec(1600, fold = vFold(1600, 10, 2000))))
        assertEquals(plain, GeometryCalculator.compute(spec(1600, fold = vFold(-50, 40, 2000))))
        assertNull(GeometryCalculator.compute(spec(1600, fold = vFold(2000, 10, 2000))).fold)
        val below = vFold(800, 0, 2000).copy(top = 2000, bottom = 2100)
        assertEquals(plain, GeometryCalculator.compute(spec(1600, fold = below)))
    }

    @Test
    fun `a non separating fold does not change the columns`() {
        val plain = GeometryCalculator.compute(spec(1600))
        val g = GeometryCalculator.compute(spec(1600, fold = vFold(800, 0, 2000, separating = false)))
        assertEquals(plain.columnLefts, g.columnLefts)
        assertEquals(plain.columnWidths, g.columnWidths)
        assertEquals(plain.gutter, g.gutter)
    }

    @Test
    fun `horizontal folds do not change column geometry`() {
        val plain = GeometryCalculator.compute(spec(1100))
        val table =
            FoldSpec(0, 1000, 1100, 1000, true, FoldOrientation.Horizontal, halfOpened = true, FoldOcclusion.None)
        val g = GeometryCalculator.compute(spec(1100, fold = table))
        assertEquals(plain.columnLefts, g.columnLefts)
        assertEquals(plain.columnWidths, g.columnWidths)
        assertEquals(plain.columnCount, g.columnCount)
        assertEquals(plain.gutter, g.gutter)
        assertEquals(plain.innerMargin, g.innerMargin)
    }

    @Test
    fun `no fold keeps gutter equal to the base gap`() {
        val g = GeometryCalculator.compute(spec(1700))
        assertEquals(g.baseGap, g.gutter)
    }

    @Test
    fun `a fold wider than the window never crashes and keeps a valid column`() {
        val g = GeometryCalculator.compute(spec(500, fold = vFold(-100, 900, 2000, occlusion = FoldOcclusion.Full)))
        assertEquals(1, g.columnCount)
        assertTrue(g.columnWidths[0] >= 1)
        assertTrue(g.columnLefts[0] >= 0 && g.columnLefts[0] + g.columnWidths[0] <= 500)
    }

    @Test
    fun `LAY-10 foldable inner display in book posture gets two columns at the default size`() {
        // 2256x2504 px, density 3, 18sp body, charW 27 px. Book: innerMargin 48 + 36 = 84, gutter 168,
        // outer 135, each side 909 px = 33.7 charW. Fold floor 28 x 27 = 756.
        val g = GeometryCalculator.compute(spec(2256, 2504, 3f, 27f, fold = vFold(1128, 0, 2504, halfOpened = true)))
        assertEquals(2, g.columnCount)
        assertEquals(listOf(909, 909), g.columnWidths)
        assertEquals(168, g.gutter)
        // Flat with a 24dp occluding hinge: same gutter, still two columns.
        val flat = vFold(1092, 72, 2504, occlusion = FoldOcclusion.Full)
        assertEquals(2, GeometryCalculator.compute(spec(2256, 2504, 3f, 27f, fold = flat)).columnCount)
    }

    @Test
    fun `LAY-10 the fold floor is 28 charW per side, a side just under it falls back to one column`() {
        // charW 10, density 1: outer 60 at w 1000, gutter 32. Fold at 500: 60..484 = 424 per side.
        // A fold at 500 in a 770 px window: outer 46, sides 46..484 = 438 and 516..724 = 208: one column.
        assertEquals(1, GeometryCalculator.compute(spec(770, fold = vFold(500, 0, 2000))).columnCount)
        // w 680: outer 41, gutter 32, sides 340 - 16 - 41 = 283 >= 280: fits.
        val fits = GeometryCalculator.compute(spec(680, fold = vFold(340, 0, 2000)))
        assertEquals(2, fits.columnCount)
        // w 640: outer 38, sides 266 (< 280) do not.
        assertEquals(1, GeometryCalculator.compute(spec(640, fold = vFold(320, 0, 2000))).columnCount)
    }

    @Test
    fun `LAY-10 a phone in book posture and a 1_5x font on the foldable keep one column`() {
        // Phone 1080x2400 at density 2.75, charW 24.75: book gutter and margins leave sides near 14 charW.
        val phone = spec(1080, 2400, 2.75f, 24.75f, fold = vFold(540, 0, 2400, halfOpened = true))
        assertEquals(1, GeometryCalculator.compute(phone).columnCount)
        // Foldable at 1.5x font: charW 40.5, sides 896 px = 22 charW < 28.
        val big = spec(2256, 2504, 3f, 40.5f, fold = vFold(1128, 0, 2504, halfOpened = true))
        assertEquals(1, GeometryCalculator.compute(big).columnCount)
    }

    @Test
    fun `LAY-10 a tie between the sides puts the single column on the left, unequal rooms pick the wider`() {
        val tie = GeometryCalculator.compute(spec(600, 2000, 1f, 30f, fold = vFold(300, 0, 2000)))
        assertEquals(1, tie.columnCount)
        assertTrue(tie.columnLefts[0] + tie.columnWidths[0] <= 300)
        val right = GeometryCalculator.compute(spec(600, 2000, 1f, 30f, fold = vFold(299, 0, 2000)))
        assertTrue(right.columnLefts[0] >= 299)
    }

    @Test
    fun `LAY-10 DM-12 numbers, one column on the left side, clear of the band and inside the outer margin`() {
        // 2256x2504 px, density 3, charW 59.2 (2.0 font scale), book posture, fold at 1128 width 0.
        // innerMargin 89 + 36 = 125, gutter 250, band 1003..1253, outer 135, left room 868 < 28 charW = 1658.
        val g = GeometryCalculator.compute(spec(2256, 2504, 3f, 59.2f, fold = vFold(1128, 0, 2504, halfOpened = true)))
        assertEquals(1, g.columnCount)
        assertEquals(listOf(868), g.columnWidths)
        assertEquals(listOf(135), g.columnLefts)
        assertTrue(g.columnLefts[0] + g.columnWidths[0] <= 1128 - g.innerMargin)
    }

    @Test
    fun `LAY-10 the single fallback column is centered in the window when that clears the fold band`() {
        // charW 10, density 1, w 2000, fold at 1700 width 0: outer 120, gutter band 1684..1716.
        // Left room 1564 holds the 750 px maxMeasure; right room 164 < 280. Centered: (2000 - 750) / 2 = 625.
        val g = GeometryCalculator.compute(spec(2000, fold = vFold(1700, 0, 2000)))
        assertEquals(1, g.columnCount)
        assertEquals(listOf(750), g.columnWidths)
        assertEquals(listOf(625), g.columnLefts)
        // Mirror: fold at 300, the column sits on the right side and is centered in the window all the same.
        val m = GeometryCalculator.compute(spec(2000, fold = vFold(300, 0, 2000)))
        assertEquals(listOf(625), m.columnLefts)
    }

    @Test
    fun `LAY-10 a centered fallback column that would intrude shifts the least amount away from the fold`() {
        // charW 10, w 1000, fold at 850 width 0: outer 60, band 834..866, left room 774, right room 74 (< 280).
        // Width 750; centered would span 125..875, past 834, so it ends at 834 instead: left = 84.
        val g = GeometryCalculator.compute(spec(1000, fold = vFold(850, 0, 2000)))
        assertEquals(1, g.columnCount)
        assertEquals(listOf(84), g.columnLefts)
        assertEquals(834, g.columnLefts[0] + g.columnWidths[0])
    }

    @Test
    fun `property single fallback column never touches the fold band and stays inside the outer margins`() {
        val rs = RandomSource.seeded(SEED)
        var seen = 0
        repeat(ITERATIONS * 3) {
            val w = Arb.int(200..5000).next(rs)
            val density = Arb.float(1f..4f).next(rs)
            val charW = Arb.float(6f..60f).next(rs)
            val foldW = Arb.int(0..120).next(rs)
            val foldLeft = Arb.int(0..(w - foldW).coerceAtLeast(0)).next(rs)
            val fold = vFold(foldLeft, foldW, 3000, halfOpened = Arb.int(0..1).next(rs) == 1)
            val label = "seed=$SEED w=$w density=$density charW=$charW fold=$fold"
            val g = GeometryCalculator.compute(WindowSpec(w, 3000, density, charW, fold = fold))
            val zoneLeft = foldLeft - g.innerMargin
            val zoneRight = foldLeft + foldW + g.innerMargin
            val noRoom = maxOf(zoneLeft - g.outerMargin, w - g.outerMargin - zoneRight) < 1
            if (g.columnCount == 1 && !noRoom) {
                seen++
                val l = g.columnLefts[0]
                val r = l + g.columnWidths[0]
                assertTrue(r <= zoneLeft || l >= zoneRight, "$label column [$l,$r) hits [$zoneLeft,$zoneRight)")
                assertTrue(l >= g.outerMargin && r <= w - g.outerMargin, "$label outside outer margins [$l,$r)")
                assertTrue(g.columnWidths[0] <= g.maxMeasure, "$label wider than maxMeasure")
                val centered = (w - g.columnWidths[0]) / 2
                val gutterLeft = Math.floorDiv(2 * foldLeft + foldW - g.gutter, 2)
                val centeredClears = centered + g.columnWidths[0] <= gutterLeft || centered >= gutterLeft + g.gutter
                if (centeredClears && centered >= g.outerMargin && centered + g.columnWidths[0] <= w - g.outerMargin) {
                    assertEquals(centered, l, "$label centered in the window when it clears")
                }
            }
        }
        assertTrue(seen > 50, "property exercised the fallback only $seen times")
    }

    @Test
    fun `LAY-14 applies to separating folds only, a non separating fold gives plain column math`() {
        val rs = RandomSource.seeded(SEED)
        repeat(ITERATIONS) {
            val w = Arb.int(200..5000).next(rs)
            val charW = Arb.float(6f..30f).next(rs)
            val foldW = Arb.int(0..120).next(rs)
            val foldLeft = Arb.int(0..(w - foldW).coerceAtLeast(0)).next(rs)
            val book = Arb.int(0..1).next(rs) == 1
            val fold = vFold(foldLeft, foldW, 2000, separating = false, halfOpened = book)
            val label = "seed=$SEED w=$w charW=$charW forced fold=$fold"
            val a = GeometryCalculator.compute(WindowSpec(w, 2000, 1f, charW))
            val b = GeometryCalculator.compute(WindowSpec(w, 2000, 1f, charW, fold = fold))
            assertEquals(a.columnLefts, b.columnLefts, "$label lefts")
            assertEquals(a.columnWidths, b.columnWidths, "$label widths")
            assertEquals(a.gutter, b.gutter, "$label gutter")
            assertEquals(a.columnCount, b.columnCount, "$label count")
        }
    }

    @Test
    fun `property fold geometry invariants hold for any window and fold`() {
        val rs = RandomSource.seeded(SEED)
        repeat(ITERATIONS) {
            val w = Arb.int(200..5000).next(rs)
            val h = Arb.int(300..4000).next(rs)
            val density = Arb.float(1f..4f).next(rs)
            val charW = Arb.float(6f..30f).next(rs)
            val forced = listOf(null, 1, 2, 3)[Arb.int(0..3).next(rs)]
            val full = Arb.int(0..1).next(rs) == 1
            val foldW = Arb.int(0..120).next(rs)
            val foldLeft = Arb.int(0..(w - foldW).coerceAtLeast(0)).next(rs)
            val book = Arb.int(0..1).next(rs) == 1
            val fold =
                vFold(
                    foldLeft,
                    foldW,
                    h,
                    separating = true,
                    halfOpened = book,
                    occlusion = if (full) FoldOcclusion.Full else FoldOcclusion.None,
                )
            val label =
                "seed=$SEED w=$w h=$h density=$density charW=$charW forced=$forced fold=$fold"
            val g = GeometryCalculator.compute(WindowSpec(w, h, density, charW, forcedColumns = forced, fold = fold))

            val inner = Math.round(maxOf(16f * density, 1.5f * charW)) + if (book) Math.round(12f * density) else 0
            assertEquals(inner, g.innerMargin, "$label innerMargin")
            val expectedGutter = maxOf(g.baseGap, foldW + 2 * inner)
            assertEquals(expectedGutter, g.gutter, "$label gutter formula")
            assertTrue(g.columnCount == 1 || g.columnCount == 2, "$label even or one-sided: ${g.columnCount}")

            val zoneLeft = foldLeft - inner
            val zoneRight = foldLeft + foldW + inner
            if (g.columnCount == 2) {
                val gap = g.columnLefts[1] - g.columnLefts[0] - g.columnWidths[0]
                assertEquals(expectedGutter, gap, "$label gap equals gutter")
                val centerGap = 2 * g.columnLefts[0] + 2 * g.columnWidths[0] + gap
                assertTrue(abs(centerGap - (2 * foldLeft + foldW)) <= 2, "$label centered")
                assertTrue(g.columnWidths.all { it >= Math.round(28f * charW) }, "$label sides below fold floor")
            }
            // LAY-14: no column rectangle intersects the fold expanded by innerMargin, unless the fold left
            // no room at all (degenerate fallback, which is only allowed when no side can hold a column).
            val l0 = g.columnLefts[0]
            val degenerate = g.columnCount == 1 && l0 < zoneRight && l0 + g.columnWidths[0] > zoneLeft
            if (!degenerate) {
                for (i in 0 until g.columnCount) {
                    val l = g.columnLefts[i]
                    val r = l + g.columnWidths[i]
                    assertTrue(r <= zoneLeft || l >= zoneRight, "$label column $i [$l,$r) hits [$zoneLeft,$zoneRight)")
                }
            } else {
                val leftRoom = zoneLeft - g.outerMargin
                val rightRoom = w - g.outerMargin - zoneRight
                assertTrue(maxOf(leftRoom, rightRoom) < 1, "$label degenerate only when no side has room")
            }
            val inside = g.columnLefts.zip(g.columnWidths).all { (l, cw) -> l >= 0 && l + cw <= w && cw >= 1 }
            assertTrue(inside, "$label inside window")
            assertEquals(g.columnLefts.first(), g.leftMargin, "$label leftMargin")
            assertEquals(w - g.columnLefts.last() - g.columnWidths.last(), g.rightMargin, "$label rightMargin")
        }
    }

    @Test
    fun `property a non intersecting fold equals no fold`() {
        val rs = RandomSource.seeded(SEED)
        repeat(ITERATIONS) {
            val w = Arb.int(200..5000).next(rs)
            val h = Arb.int(300..4000).next(rs)
            val charW = Arb.float(6f..30f).next(rs)
            val outside = Arb.int(0..500).next(rs)
            val fold = vFold(w + outside, Arb.int(0..50).next(rs), h, occlusion = FoldOcclusion.Full)
            val a = GeometryCalculator.compute(WindowSpec(w, h, 1f, charW))
            val b = GeometryCalculator.compute(WindowSpec(w, h, 1f, charW, fold = fold))
            assertEquals(a, b, "seed=$SEED w=$w h=$h charW=$charW fold=$fold")
        }
    }

    private companion object {
        const val SEED = 20261018L
        const val ITERATIONS = 600
    }
}
