package dev.dcesares.bifolio.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** POS-04 (tabletop text region and deck), LAY-08 (page height from the region), DM-07, PAG-10. */
class TabletopGeometryTest {
    private fun hFold(
        top: Int,
        height: Int,
        width: Int,
        halfOpened: Boolean = true,
        separating: Boolean = true,
        occlusion: FoldOcclusion = FoldOcclusion.None,
    ) = FoldSpec(0, top, width, top + height, separating, FoldOrientation.Horizontal, halfOpened, occlusion)

    private fun spec(
        w: Int,
        h: Int,
        charW: Float,
        density: Float = 1f,
        lineHeight: Float = 0f,
        forced: Int? = null,
        fold: FoldSpec? = null,
    ) = WindowSpec(
        widthPx = w,
        heightPx = h,
        density = density,
        charWidthPx = charW,
        forcedColumns = forced,
        fold = fold,
        bodyLineHeightPx = lineHeight,
    )

    @Test
    fun `DM-07 inner screen 2256x2504 at 480dpi, fold at y 1252 height 0, one column`() {
        val g = GeometryCalculator.compute(spec(2256, 2504, 27f, 3f, 60f, fold = hFold(1252, 0, 2256)))
        assertEquals(Posture.Tabletop, g.posture)
        assertEquals(PxRect(0, 0, 2256, 1252), g.textRegion)
        assertEquals(PxRect(0, 1252, 2256, 2504), g.deckRegion)
        assertEquals(135, g.outerMargin) // 0.06 * 2256 = 135.36 on the full width
        assertEquals(135, g.verticalMargin) // min(135, 1252 / 8 = 156)
        assertEquals(1252 - 2 * 135, g.columnHeight)
        assertEquals(1, g.columnCount)
        assertEquals(listOf(1986), g.columnWidths) // 2256 - 270, under maxMeasure 2025
        assertEquals(2504, g.windowHeight)
    }

    @Test
    fun `DM-07 inner screen with a small face gets two columns from the region`() {
        val g = GeometryCalculator.compute(spec(2256, 2504, 18f, 3f, 40f, fold = hFold(1252, 0, 2256)))
        assertEquals(2, g.columnCount)
        assertEquals(listOf(945, 945), g.columnWidths) // (1986 - 96) / 2
        assertEquals(1252 - 2 * 135, g.columnHeight)
        val flat = GeometryCalculator.compute(spec(2256, 2504, 18f, 3f, 40f))
        assertEquals(flat.columnWidths, g.columnWidths)
        assertEquals(flat.columnLefts, g.columnLefts)
    }

    @Test
    fun `DM-07 24dp occluding hinge is centered on y 1252, text above it, deck below it`() {
        val fold = hFold(1216, 72, 2256, occlusion = FoldOcclusion.Full)
        val g = GeometryCalculator.compute(spec(2256, 2504, 27f, 3f, 60f, fold = fold))
        assertEquals(Posture.Tabletop, g.posture)
        assertEquals(PxRect(0, 0, 2256, 1216), g.textRegion)
        assertEquals(PxRect(0, 1288, 2256, 2504), g.deckRegion)
        assertEquals(135, g.verticalMargin) // min(135, 1216 / 8 = 152)
        assertEquals(1216 - 270, g.columnHeight)
    }

    @Test
    fun `vertical margin is evaluated on the region height`() {
        // Region 400: 400 / 8 = 50 < outer 135, so the margin shrinks to 50 although 2504 / 8 would allow 135.
        val g = GeometryCalculator.compute(spec(2256, 2504, 27f, 3f, 20f, fold = hFold(400, 0, 2256)))
        assertEquals(50, g.verticalMargin)
        assertEquals(400 - 100, g.columnHeight)
    }

    @Test
    fun `region under four body lines falls back to ignoring the fold`() {
        val tooShort = GeometryCalculator.compute(spec(900, 840, 9f, lineHeight = 30f, fold = hFold(119, 0, 900)))
        val exact = GeometryCalculator.compute(spec(900, 840, 9f, lineHeight = 30f, fold = hFold(120, 0, 900)))
        val plain = GeometryCalculator.compute(spec(900, 840, 9f, lineHeight = 30f))
        assertEquals(Posture.Standard, tooShort.posture)
        assertNull(tooShort.deckRegion)
        assertEquals(PxRect(0, 0, 900, 840), tooShort.textRegion)
        assertEquals(plain.columnHeight, tooShort.columnHeight)
        assertEquals(plain.verticalMargin, tooShort.verticalMargin)
        assertEquals(Posture.Tabletop, exact.posture)
        assertEquals(PxRect(0, 0, 900, 120), exact.textRegion)
    }

    @Test
    fun `fold at the top edge is a fallback, not a crash`() {
        val g = GeometryCalculator.compute(spec(900, 840, 9f, fold = hFold(0, 0, 900)))
        assertEquals(Posture.Standard, g.posture)
        assertNull(g.deckRegion)
    }

    @Test
    fun `half-opened horizontal fold that is not separating is still tabletop`() {
        val g = GeometryCalculator.compute(spec(900, 840, 9f, fold = hFold(420, 0, 900, separating = false)))
        assertEquals(Posture.Tabletop, g.posture)
        assertEquals(PxRect(0, 0, 900, 420), g.textRegion)
        assertEquals(PxRect(0, 420, 900, 840), g.deckRegion)
    }

    @Test
    fun `flat horizontal fold and folds outside the window keep the full window`() {
        val flat = GeometryCalculator.compute(spec(900, 840, 9f, fold = hFold(420, 0, 900, halfOpened = false)))
        assertEquals(Posture.Standard, flat.posture)
        assertEquals(PxRect(0, 0, 900, 840), flat.textRegion)
        assertNull(flat.deckRegion)
        val outside = GeometryCalculator.compute(spec(900, 840, 9f, fold = hFold(900, 0, 900)))
        assertEquals(PxRect(0, 0, 900, 840), outside.textRegion)
        assertNull(outside.deckRegion)
    }

    @Test
    fun `deck under 96dp falls back to ignoring the fold, boundary by density`() {
        for (density in listOf(1f, 2f, 2.625f, 3f)) {
            val minDeck = Math.round(96 * density)
            val h = 2000
            val atLine =
                GeometryCalculator.compute(
                    spec(1200, h, 9f, density, fold = hFold(h - minDeck - 40, 40, 1200)),
                )
            val below =
                GeometryCalculator.compute(
                    spec(1200, h, 9f, density, fold = hFold(h - minDeck - 39, 40, 1200)),
                )
            assertEquals(Posture.Tabletop, atLine.posture, "density $density")
            assertEquals(PxRect(0, h - minDeck, 1200, h), atLine.deckRegion, "density $density")
            assertEquals(Posture.Standard, below.posture, "density $density")
            assertNull(below.deckRegion, "density $density")
            assertEquals(PxRect(0, 0, 1200, h), below.textRegion, "density $density")
            assertEquals(GeometryCalculator.compute(spec(1200, h, 9f, density)).columnHeight, below.columnHeight)
        }
        val reaching = GeometryCalculator.compute(spec(900, 840, 9f, fold = hFold(700, 200, 900)))
        assertEquals(Posture.Standard, reaching.posture)
        assertNull(reaching.deckRegion)
    }

    @Test
    fun `tabletop geometry differs from the flat one as a cache key and is stable for equal specs`() {
        val s = spec(900, 840, 9f, fold = hFold(420, 0, 900))
        assertEquals(GeometryCalculator.compute(s), GeometryCalculator.compute(s.copy()))
        assertTrue(GeometryCalculator.compute(s) != GeometryCalculator.compute(s.copy(fold = null)))
        assertTrue(
            GeometryCalculator.compute(s) != GeometryCalculator.compute(s.copy(fold = hFold(430, 0, 900))),
        )
    }

    @Test
    fun `property tabletop text stays above the fold and inside the margins, deck is below`() {
        val random = Random(SEED)
        repeat(ROUNDS) { round ->
            val w = random.nextInt(320, 2600)
            val h = random.nextInt(320, 2800)
            val charW = random.nextInt(6, 30).toFloat()
            val density = random.nextInt(1, 4).toFloat()
            val lineHeight = random.nextInt(14, 80).toFloat()
            val foldTop = random.nextInt(-20, h + 20)
            val foldHeight = if (random.nextBoolean()) 0 else random.nextInt(0, 120)
            val fold = hFold(foldTop, foldHeight, w, separating = random.nextBoolean())
            val s = spec(w, h, charW, density, lineHeight, forced = random.nextInt(0, 4).takeIf { it > 0 }, fold = fold)
            val g = GeometryCalculator.compute(s)
            val tag = "seed=$SEED round=$round $s"
            val flat = GeometryCalculator.compute(s.copy(fold = null))
            if (g.posture == Posture.Tabletop) {
                val region = g.textRegion
                assertEquals(0, region.top, tag)
                assertEquals(foldTop, region.bottom, tag)
                assertEquals(w, region.right, tag)
                assertTrue(region.height >= Math.ceil(4.0 * lineHeight).toInt(), tag)
                assertEquals(minOf(g.outerMargin, region.height / 8), g.verticalMargin, tag)
                assertEquals(maxOf(region.height - 2 * g.verticalMargin, 1), g.columnHeight, tag)
                assertTrue(g.verticalMargin + g.columnHeight <= foldTop, "$tag text crosses fold top")
                assertTrue(g.verticalMargin >= 0, tag)
                // Widths are plain LAY-04..09 on the full window: identical to no fold.
                assertEquals(flat.columnWidths, g.columnWidths, tag)
                assertEquals(flat.columnLefts, g.columnLefts, tag)
                assertEquals(flat.gutter, g.gutter, tag)
                for (i in 0 until g.columnCount) {
                    assertTrue(g.columnLefts[i] >= g.outerMargin, "$tag column $i left of margin")
                    assertTrue(g.columnLefts[i] + g.columnWidths[i] <= w - g.outerMargin || g.columnCount == 1, tag)
                }
                val deck = g.deckRegion!!
                assertEquals(minOf(maxOf(foldTop + foldHeight, foldTop), h), deck.top, tag)
                assertEquals(h, deck.bottom, tag)
                assertTrue(deck.top >= region.bottom, "$tag deck overlaps text")
                assertTrue(deck.height >= Math.round(96 * density), "$tag deck too short")
            } else {
                assertEquals(flat, g.copy(fold = null, posture = Posture.Standard), tag)
                assertNull(g.deckRegion, tag)
                assertEquals(PxRect(0, 0, w, h), g.textRegion, tag)
            }
        }
    }

    @Test
    fun `property standard and book geometries are unchanged by the tabletop fields`() {
        val random = Random(SEED + 1)
        repeat(ROUNDS) { round ->
            val w = random.nextInt(320, 2600)
            val h = random.nextInt(320, 2800)
            val charW = random.nextInt(6, 30).toFloat()
            val fold =
                when (random.nextInt(3)) {
                    0 -> null
                    1 ->
                        FoldSpec(
                            w / 2,
                            0,
                            w / 2 + random.nextInt(0, 60),
                            h,
                            true,
                            FoldOrientation.Vertical,
                            random.nextBoolean(),
                            FoldOcclusion.None,
                        )
                    else -> hFold(random.nextInt(0, h), 0, w, halfOpened = false)
                }
            val s = spec(w, h, charW, random.nextInt(1, 4).toFloat(), 40f, fold = fold)
            val g = GeometryCalculator.compute(s)
            val tag = "seed=${SEED + 1} round=$round $s"
            assertEquals(PxRect(0, 0, w, h), g.textRegion, tag)
            assertNull(g.deckRegion, tag)
            assertEquals(minOf(g.outerMargin, h / 8), g.verticalMargin, tag)
            assertEquals(maxOf(h - 2 * g.verticalMargin, 1), g.columnHeight, tag)
            assertEquals(g, GeometryCalculator.compute(s.copy(bodyLineHeightPx = 0f)), tag)
        }
    }

    @Test
    fun `property pagination in tabletop uses the region height`() {
        val random = Random(SEED + 2)
        val md = markdown(*List(40) { listOf("# H$it", para(7), para(3, letter = 'b')) }.flatten().toTypedArray())
        var tabletop = 0
        repeat(40) { round ->
            val w = random.nextInt(800, 1600)
            val h = random.nextInt(900, 1800)
            val fold = hFold(random.nextInt(h / 4, 3 * h / 4), random.nextInt(0, 60), w)
            val s = spec(w, h, random.nextInt(6, 13).toFloat(), lineHeight = 20f, fold = fold)
            val g = GeometryCalculator.compute(s).copy(metrics = TEST_METRICS)
            val tag = "seed=${SEED + 2} round=$round $s"
            assertEquals(Posture.Tabletop, g.posture, tag)
            tabletop++
            val (_, flow) = paginate(md, g, FakeBlockMeasurer(charWidth = 8, lineHeight = 20))
            assertEquals(g.textRegion.height - 2 * g.verticalMargin, g.columnHeight, tag)
            assertTrue(flow.pages.isNotEmpty(), tag)
            for ((pageIndex, page) in flow.pages.withIndex()) {
                assertTrue(page.usedHeight <= g.columnHeight, "$tag page $pageIndex used ${page.usedHeight}")
                assertTrue(g.verticalMargin + page.usedHeight <= fold.top, "$tag page crosses fold top")
            }
            // A taller window would fit more per page: the region, not the window, bounds the page.
            val tall = g.copy(columnHeight = g.windowHeight - 2 * g.verticalMargin)
            val (_, tallFlow) = paginate(md, tall, FakeBlockMeasurer(charWidth = 8, lineHeight = 20))
            assertTrue(tallFlow.pages.size <= flow.pages.size, tag)
        }
        assertEquals(40, tabletop)
    }

    private companion object {
        const val SEED = 20260717L
        const val ROUNDS = 400
    }
}
