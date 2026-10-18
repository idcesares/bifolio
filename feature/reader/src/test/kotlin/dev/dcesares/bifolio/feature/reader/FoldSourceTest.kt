package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.FoldOcclusion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.Posture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val BOOK_FOLD =
    FoldSpec(
        left = 420,
        top = 0,
        right = 420,
        bottom = 900,
        isSeparating = true,
        orientation = FoldOrientation.Vertical,
        halfOpened = true,
        occlusion = FoldOcclusion.None,
    )

private fun request(fold: FoldSpec?) =
    PageRequest(
        widthPx = 840,
        heightPx = 900,
        density = 1f,
        fontScale = 1f,
        bodySp = 18f,
        languageTag = "en",
        fold = fold,
    )

/** LAY-01, LAY-02, POS-02, POS-03, POS-05: fold input mapping, inset translation and request identity. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoldSourceTest {
    @Test
    fun `half opened vertical separating feature maps to a book fold`() {
        val spec = foldSpecOf(true, true, false, true, 410, 0, 430, 900)
        assertEquals(FoldSpec(410, 0, 430, 900, true, FoldOrientation.Vertical, true, FoldOcclusion.None), spec)
    }

    @Test
    fun `flat horizontal feature keeps orientation, state and bounds`() {
        val spec = foldSpecOf(false, false, false, false, 0, 440, 900, 460)
        assertEquals(FoldOrientation.Horizontal, spec.orientation)
        assertEquals(false, spec.halfOpened)
        assertEquals(false, spec.isSeparating)
        assertEquals(440, spec.top)
    }

    @Test
    fun `full occlusion implies separating even when the source says otherwise`() {
        val spec = foldSpecOf(false, true, true, false, 408, 0, 432, 900)
        assertTrue(spec.isSeparating)
        assertEquals(FoldOcclusion.Full, spec.occlusion)
    }

    @Test
    fun `inverted bounds are normalized instead of failing`() {
        val spec = foldSpecOf(false, true, false, true, 430, 900, 410, 0)
        assertEquals(410, spec.left)
        assertEquals(430, spec.right)
        assertEquals(0, spec.top)
        assertEquals(900, spec.bottom)
    }

    @Test
    fun `translation shifts the fold by the left and top insets only`() {
        val shifted = BOOK_FOLD.translatedBy(insetLeftPx = 48, insetTopPx = 100)
        assertEquals(372, shifted.left)
        assertEquals(372, shifted.right)
        assertEquals(-100, shifted.top)
        assertEquals(800, shifted.bottom)
        assertEquals(BOOK_FOLD.copy(left = 420, right = 420), BOOK_FOLD.translatedBy(0, 0))
    }

    @Test
    fun `a fold that stays inside the content area still reshapes the columns after translation`() {
        val area = request(BOOK_FOLD.copy(left = 648, right = 648).translatedBy(48, 0)).copy(widthPx = 1200)
        val geometry = GeometryCalculator.compute(GridTextEngine().prepare(area).spec)
        assertEquals(Posture.Book, geometry.posture)
        assertEquals(2, geometry.columnCount)
    }

    @Test
    fun `a fold partly outside the content area is kept and classified`() {
        val partly = BOOK_FOLD.copy(left = -10, right = 6).translatedBy(0, 0)
        val geometry = GeometryCalculator.compute(GridTextEngine().prepare(request(partly)).spec)
        assertEquals(partly, geometry.fold)
        assertEquals(Posture.Book, geometry.posture)
    }

    @Test
    fun `a fold fully outside after translation is dropped by the geometry rule`() {
        // Hinge at x 420 with an 840 px wide content area that starts 900 px to its right: nothing intersects.
        val outside = BOOK_FOLD.translatedBy(insetLeftPx = 900, insetTopPx = 0)
        val geometry = GeometryCalculator.compute(GridTextEngine().prepare(request(outside)).spec)
        assertNull(geometry.fold)
        assertEquals(Posture.Standard, geometry.posture)
        assertEquals(GeometryCalculator.compute(GridTextEngine().prepare(request(null)).spec), geometry)
    }

    @Test
    fun `fold and posture are part of request identity and geometry`() {
        val none = request(null)
        val book = request(BOOK_FOLD)
        val moved = request(BOOK_FOLD.copy(left = 400, right = 440))
        assertNotEquals(none, book)
        assertNotEquals(book, moved)
        val geometries =
            listOf(none, book, moved).map { GeometryCalculator.compute(GridTextEngine().prepare(it).spec) }
        assertEquals(3, geometries.toSet().size)
        assertEquals(listOf(Posture.Standard, Posture.Book, Posture.Book), geometries.map { it.posture })
    }

    @Test
    fun `fake source replays the latest fold and follows changes`() =
        runTest {
            val source = FakeFoldSource()
            source.emit(BOOK_FOLD)
            val seen = mutableListOf<FoldSpec?>()
            val job = launch { source.fold.collect { seen.add(it) } }
            testScheduler.runCurrent()
            source.emit(null)
            testScheduler.runCurrent()
            job.cancel()
            assertEquals(listOf(BOOK_FOLD, null), seen)
        }

    @Test
    fun `the first request is not delayed`() =
        runTest {
            val out = mutableListOf<Pair<Int, Long>>()
            flowOf(1).debounceAfterFirst(DEBOUNCE).collect { out.add(it to currentTime) }
            assertEquals(listOf(1 to 0L), out)
        }

    @Test
    fun `a burst collapses to its last request after the quiet period`() =
        runTest {
            val input = Channel<Int>(Channel.UNLIMITED)
            val out = mutableListOf<Pair<Int, Long>>()
            val job =
                launch { input.receiveAsFlow().debounceAfterFirst(DEBOUNCE).collect { out.add(it to currentTime) } }
            input.send(1)
            testScheduler.runCurrent()
            assertEquals(listOf(1 to 0L), out)
            input.send(2)
            advanceTimeBy(DEBOUNCE / 2)
            input.send(3)
            advanceTimeBy(DEBOUNCE / 2)
            input.send(4)
            advanceTimeBy(DEBOUNCE - 1)
            assertEquals(1, out.size)
            advanceTimeBy(1)
            testScheduler.runCurrent()
            assertEquals(listOf(1 to 0L, 4 to DEBOUNCE * 2), out)
            job.cancel()
        }

    private companion object {
        const val DEBOUNCE = 100L
    }
}
