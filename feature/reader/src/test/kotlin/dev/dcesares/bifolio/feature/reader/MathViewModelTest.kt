package dev.dcesares.bifolio.feature.reader

import android.graphics.Bitmap
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.math.MathFailure
import dev.dcesares.bifolio.math.MathMeasurement
import dev.dcesares.bifolio.math.MathService
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

private val PHONE =
    PageRequest(widthPx = 400, heightPx = 640, density = 1f, fontScale = 1f, bodySp = 18f, languageTag = "en")

private const val TEXT = "Before.\n\n$$\nx^2\n$$\n\nBetween.\n\n$$\n\\frac{a}{b}\n$$\n\nAfter.\n"

private class FakeMath(
    override val density: Float = 1f,
    override val fontScale: Float = 1f,
    private val answer: (String) -> MathMeasurement = { MathMeasurement.Sized(120, 40) },
) : MathService {
    var hold: CompletableDeferred<Unit>? = null
    var cancelled = false
    val measured = AtomicInteger(0)

    override suspend fun measure(
        tex: String,
        fontSp: Float,
        display: Boolean,
    ): MathMeasurement {
        try {
            hold?.await()
        } catch (c: kotlinx.coroutines.CancellationException) {
            cancelled = true
            throw c
        }
        measured.incrementAndGet()
        return answer(tex)
    }

    override suspend fun render(
        tex: String,
        fontSp: Float,
        scale: Float,
        display: Boolean,
    ): Bitmap? = null
}

/** SYN-12 and PAG-07 through the real view model: formula sizes repaginate from the anchor (ANC-03), once. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MathViewModelTest {
    private val grid = GridTextEngine()
    private val prepares = AtomicInteger(0)

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun opened(): ReaderViewModel {
        val vm =
            ReaderViewModel(
                reader = FakeReader(mapOf("doc" to raw(TEXT))),
                repository = RecentsRepository(InMemoryPreferences(), RecordingGrants()),
                preferences = ReadingPreferences(InMemoryPreferences()),
                engine = { request -> prepares.incrementAndGet().let { grid.prepare(request) } },
                computeDispatcher = Dispatchers.Main,
            )
        vm.open(OpenRequest("doc", "text/markdown", persistGrant = false))
        return vm
    }

    @Test
    fun sizesArrivingRepaginateExactlyOnceFromTheCurrentAnchor() {
        val vm = opened()
        vm.paginate(PHONE)
        assertEquals(1, prepares.get())
        assertEquals(0, vm.paged.value?.mathKey)
        val at = Anchor(2, 0)
        vm.onAnchorSettled(at)

        vm.bindMath(FakeMath(), 18f)

        val session = checkNotNull(vm.mathSession.value)
        assertEquals(2, session.blocks.size)
        val snapshot = session.sizes.value
        assertTrue(snapshot.key != 0)
        assertEquals("one repagination for the whole batch", 2, prepares.get())
        assertEquals(snapshot.key, vm.paged.value?.mathKey)
        assertEquals(at, vm.anchor.value)
        assertTrue(checkNotNull(vm.paged.value).flow.spreadIndexOf(vm.anchor.value) >= 0)
    }

    @Test
    fun anEmptySnapshotDoesNotRepaginate() {
        val vm = opened()
        vm.paginate(PHONE)
        vm.bindMath(FakeMath(answer = { MathMeasurement.Failed(MathFailure.PARSE) }), 18f)
        val session = checkNotNull(vm.mathSession.value)
        assertTrue(session.blocks.all { session.renderer.hasFailed(it) })
        assertEquals(1, prepares.get())
        assertEquals(0, vm.paged.value?.mathKey)
    }

    @Test
    fun sizesMeasuredForOtherTextSettingsDoNotRepaginate() {
        val vm = opened()
        vm.paginate(PHONE)
        vm.bindMath(FakeMath(density = 2f), 18f)
        assertEquals("density differs from the request", 1, prepares.get())
        vm.bindMath(FakeMath(fontScale = 1.3f), 18f)
        assertEquals("font scale differs from the request", 1, prepares.get())
        vm.bindMath(FakeMath(), 20f)
        assertEquals("font size differs from the request", 1, prepares.get())
        assertEquals(0, vm.paged.value?.mathKey)
    }

    @Test
    fun closingCancelsTheMeasuringAndForgetsTheSession() {
        val vm = opened()
        vm.paginate(PHONE)
        val math = FakeMath().also { it.hold = CompletableDeferred() }
        vm.bindMath(math, 18f)
        assertNotNull(vm.mathSession.value)
        assertFalse(math.cancelled)

        vm.close()

        assertTrue(math.cancelled)
        assertNull(vm.mathSession.value)
        assertEquals(0, math.measured.get())
        assertEquals(1, prepares.get())
    }

    @Test
    fun openingAnotherDocumentCancelsTheMeasuringOfTheFirst() {
        val vm = opened()
        vm.paginate(PHONE)
        val math = FakeMath().also { it.hold = CompletableDeferred() }
        vm.bindMath(math, 18f)

        vm.open(OpenRequest("doc", "text/markdown", persistGrant = false))

        assertTrue(math.cancelled)
        assertNull(vm.mathSession.value)
    }
}
