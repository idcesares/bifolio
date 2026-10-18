package dev.dcesares.bifolio.layout

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** POS-01 to POS-04, POS-05 (fold outside the window), PAG-10 (posture and fold in the cache key). */
class PostureTest {
    private fun fold(
        orientation: FoldOrientation,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        separating: Boolean,
        halfOpened: Boolean,
        occlusion: FoldOcclusion = FoldOcclusion.None,
    ) = FoldSpec(left, top, right, bottom, separating, orientation, halfOpened, occlusion)

    private fun window(
        w: Int,
        h: Int,
        fold: FoldSpec? = null,
    ) = WindowSpec(widthPx = w, heightPx = h, density = 1f, charWidthPx = 9f, fold = fold)

    private val v = FoldOrientation.Vertical
    private val hz = FoldOrientation.Horizontal

    private val matrix: List<Triple<String, WindowSpec, Posture>> =
        listOf(
            Triple("DM-01", window(412, 915), Posture.Standard),
            Triple("DM-02", window(915, 412), Posture.Standard),
            Triple("DM-03", window(360, 800), Posture.Standard),
            Triple("DM-04", window(840, 900, fold(v, 420, 0, 420, 900, false, false)), Posture.Standard),
            Triple("DM-05", window(840, 900, fold(v, 420, 0, 420, 900, true, true)), Posture.Book),
            Triple(
                "DM-06",
                window(840, 900, fold(v, 408, 0, 432, 900, true, false, FoldOcclusion.Full)),
                Posture.Book,
            ),
            Triple("DM-07", window(900, 840, fold(hz, 0, 420, 900, 420, true, true)), Posture.Tabletop),
            Triple("DM-08", window(960, 720, fold(v, 480, 0, 480, 720, false, false)), Posture.Standard),
            Triple("DM-09", window(1280, 900), Posture.Standard),
            Triple("DM-10", window(320, 640), Posture.Standard),
            Triple("DM-11", window(420, 900, fold(v, 1000, 0, 1000, 900, true, false)), Posture.Standard),
            Triple("DM-12", window(840, 900, fold(v, 420, 0, 420, 900, true, true)), Posture.Book),
        )

    @Test
    fun deviceMatrixRows() {
        for ((id, spec, expected) in matrix) {
            assertEquals(expected, PostureClassifier.classify(spec), id)
            assertEquals(expected, GeometryCalculator.compute(spec).posture, "$id geometry")
        }
    }

    @Test
    fun flatSeparatingVerticalFoldIsBook() {
        val f = fold(v, 420, 0, 420, 900, separating = true, halfOpened = false)
        assertEquals(Posture.Book, PostureClassifier.classify(window(840, 900, f)))
    }

    @Test
    fun horizontalFoldIsTabletopOnlyWhenHalfOpened() {
        val flatSep = fold(hz, 0, 420, 900, 420, separating = true, halfOpened = false)
        val half = fold(hz, 0, 420, 900, 420, separating = false, halfOpened = true)
        assertEquals(Posture.Standard, PostureClassifier.classify(window(900, 840, flatSep)))
        assertEquals(Posture.Tabletop, PostureClassifier.classify(window(900, 840, half)))
    }

    @Test
    fun classificationIgnoresWindowSizeAndNonFoldFields() {
        val rnd = RandomSource.seeded(7)
        repeat(300) {
            val o = if (Arb.boolean().next(rnd)) v else hz
            val sep = Arb.boolean().next(rnd)
            val half = Arb.boolean().next(rnd)
            val expected =
                when {
                    o == v && (sep || half) -> Posture.Book
                    o == hz && half -> Posture.Tabletop
                    else -> Posture.Standard
                }
            val w = Arb.int(200..3000).next(rnd)
            val h = Arb.int(200..3000).next(rnd)
            val f =
                if (o == v) fold(o, w / 2, 0, w / 2 + 10, h, sep, half) else fold(o, 0, h / 2, w, h / 2 + 10, sep, half)
            val spec = window(w, h, f)
            assertEquals(expected, PostureClassifier.classify(spec), "seed 7 $spec")
            val retuned = spec.copy(density = 3f, charWidthPx = 20f, forcedColumns = 1)
            assertEquals(expected, PostureClassifier.classify(retuned))
            assertEquals(Posture.Standard, PostureClassifier.classify(spec.copy(fold = null)))
            val outside = f.copy(left = w + 5, right = w + 15, top = 0, bottom = h)
            assertEquals(Posture.Standard, PostureClassifier.classify(spec.copy(fold = outside)), "outside seed 7")
        }
    }

    @Test
    fun cacheKeysDifferByFoldAndPostureAndMatchOtherwise() {
        val book = window(840, 900, fold(v, 420, 0, 420, 900, true, true))
        val flatNotSep = window(840, 900, fold(v, 420, 0, 420, 900, false, false))
        val tabletop = window(840, 900, fold(hz, 0, 450, 840, 450, true, true))
        val none = window(840, 900)
        val keys = listOf(book, flatNotSep, tabletop, none).map { LayoutKey("h", GeometryCalculator.compute(it)) }
        assertEquals(4, keys.toSet().size)
        val again = LayoutKey("h", GeometryCalculator.compute(book.copy()))
        assertEquals(keys[0], again)
        assertEquals(keys[0].hashCode(), again.hashCode())
        val g = GeometryCalculator.compute(none)
        assertNotEquals(LayoutKey("h", g), LayoutKey("h", g.copy(posture = Posture.Tabletop)))
        val moved = window(840, 900, fold(v, 400, 0, 400, 900, true, true))
        assertNotEquals(keys[0], LayoutKey("h", GeometryCalculator.compute(moved)))
    }
}
