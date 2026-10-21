package dev.dcesares.bifolio.math

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** A backend that counts, sleeps, advances a clock or throws on demand: the guards of ADR-0006 without the library. */
private class FakeBackend : MathBackend {
    val measured = CopyOnWriteArrayList<String>()
    val exported = AtomicInteger(0)
    val parsed = AtomicInteger(0)
    private val running = AtomicInteger(0)
    val peak = AtomicInteger(0)
    var clock = 0L
    var cost = 0L
    var parses = true
    var sleepMillis = 0L
    var dims: (String) -> Dims? = { Dims(120f, 40f) }
    var fault: (() -> Throwable)? = null
    var exportFault: (() -> Throwable)? = null

    override fun parsesCleanly(prepared: String): Boolean {
        parsed.incrementAndGet()
        fault?.let { if (faultOn == Where.PARSE) throw it() }
        return parses
    }

    var faultOn = Where.MEASURE

    enum class Where { PARSE, MEASURE }

    override fun measure(
        prepared: String,
        fontSp: Float,
        display: Boolean,
    ): Dims? {
        val now = running.incrementAndGet()
        peak.updateAndGet { maxOf(it, now) }
        try {
            if (sleepMillis > 0) Thread.sleep(sleepMillis)
            measured.add(prepared)
            clock += cost
            fault?.let { if (faultOn == Where.MEASURE) throw it() }
            return dims(prepared)
        } finally {
            running.decrementAndGet()
        }
    }

    override fun export(
        prepared: String,
        fontSp: Float,
        scale: Float,
        display: Boolean,
    ): Bitmap? {
        exported.incrementAndGet()
        clock += cost
        exportFault?.let { throw it() }
        return null
    }
}

class MathEngineGuardsTest {
    private val backend = FakeBackend()
    private val engine = MathEngine(backend, density = 2f, fontScale = 1f, clockMillis = { backend.clock })

    private fun measure(
        tex: String,
        sp: Float = 18f,
    ) = runBlocking { engine.measure(tex, sp) }

    @Test
    fun aSizedAnswerCarriesTheRoundedUpPixelSize() {
        backend.dims = { Dims(120.2f, 39.5f) }
        assertEquals(MathMeasurement.Sized(121, 40), measure("x^2"))
    }

    @Test
    fun everyKindOfThrowableEndsAsARendererFailureAndTheEngineKeepsWorking() {
        val faults: List<() -> Throwable> =
            listOf(
                { StackOverflowError() },
                { OutOfMemoryError() },
                { AssertionError("x") },
                { IllegalStateException("x") },
                { NullPointerException() },
                { LinkageError() },
            )
        for (where in FakeBackend.Where.entries) {
            for ((n, fault) in faults.withIndex()) {
                backend.faultOn = where
                backend.fault = fault
                assertEquals(MathMeasurement.Failed(MathFailure.RENDERER), measure("a+$n$where"), "$where $n")
            }
        }
        backend.fault = null
        assertEquals(MathMeasurement.Sized(120, 40), measure("a+b"))
    }

    @Test
    fun aThrowingExportEndsAsNoBitmap() {
        for (fault in listOf<() -> Throwable>({ StackOverflowError() }, { OutOfMemoryError() })) {
            backend.exportFault = fault
            assertNull(runBlocking { engine.render("a+b", 18f, 1f) })
        }
        assertTrue(backend.exported.get() >= 2)
    }

    @Test
    fun cancellationIsNotSwallowedAsAFailure() {
        backend.fault = { CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { measure("a") }
    }

    @Test
    fun theRendererIsNeverEnteredByTwoCallsAtOnce() {
        backend.sleepMillis = 2
        val answers =
            runBlocking {
                (1..40)
                    .map { async(Dispatchers.Default) { engine.measure("x_{$it}", 18f) } }
                    .awaitAll()
            }
        assertEquals(40, backend.measured.size)
        assertEquals(1, backend.peak.get())
        assertTrue(answers.all { it is MathMeasurement.Sized })
    }

    @Test
    fun anAnswerIsRememberedSoTheRendererSeesEachFormulaOnce() {
        repeat(5) { measure("a+b") }
        measure("a+b", sp = 20f)
        assertEquals(2, backend.measured.size)
        backend.dims = { null }
        repeat(3) { assertEquals(MathMeasurement.Failed(MathFailure.SIZE), measure("c+d")) }
        assertEquals(3, backend.measured.size)
    }

    @Test
    fun aDrawingAfterAMeasurementDoesNotMeasureAgain() {
        measure("a+b")
        runBlocking { engine.render("a+b", 18f, 1f) }
        runBlocking { engine.render("a+b", 18f, 0.5f) }
        assertEquals(1, backend.measured.size)
        assertEquals(2, backend.exported.get())
    }

    @Test
    fun theSizeBoundsAreExactlyTheLimitsInEm() {
        val fontPx = 18f * 2f
        backend.dims = { Dims(fontPx * MathLimits.MAX_WIDTH_EM, fontPx * MathLimits.MAX_HEIGHT_EM) }
        assertTrue(measure("at the limit") is MathMeasurement.Sized)
        val sizeFailure = MathMeasurement.Failed(MathFailure.SIZE)
        backend.dims = { Dims(fontPx * MathLimits.MAX_WIDTH_EM + 1f, 40f) }
        assertEquals(sizeFailure, measure("too wide"))
        backend.dims = { Dims(40f, fontPx * MathLimits.MAX_HEIGHT_EM + 1f) }
        assertEquals(sizeFailure, measure("too tall"))
        for (bad in listOf(
            Dims(0f, 10f),
            Dims(10f, 0f),
            Dims(-5f, 10f),
            Dims(Float.NaN, 10f),
            Dims(Float.POSITIVE_INFINITY, 10f),
        )) {
            backend.dims = { bad }
            assertEquals(sizeFailure, measure("bad ${bad.widthPx} ${bad.heightPx}"))
        }
    }

    @Test
    fun anUnknownCommandOrParseErrorNeverReachesTheMeasurer() {
        backend.parses = false
        assertEquals(MathMeasurement.Failed(MathFailure.PARSE), measure("""\unknown{x}"""))
        assertTrue(backend.measured.isEmpty())
    }

    @Test
    fun rejectedSourceNeverReachesTheRenderer() {
        val hostile =
            listOf(
                """\def\a{\a}\a""" to MathFailure.MACRO,
                """\input{/etc/passwd}""" to MathFailure.MACRO,
                "{".repeat(100) + "x" + "}".repeat(100) to MathFailure.TOO_DEEP,
                "x".repeat(MathLimits.MAX_LENGTH + 1) to MathFailure.TOO_LONG,
                """\frac{a}""" to MathFailure.MISSING_OPERAND,
                "" to MathFailure.EMPTY,
            )
        for ((tex, failure) in hostile) assertEquals(MathMeasurement.Failed(failure), measure(tex), tex.take(20))
        assertEquals(0, backend.parsed.get())
        assertTrue(backend.measured.isEmpty())
        assertNull(runBlocking { engine.render("""\def\a{\a}\a""", 18f, 1f) })
        assertEquals(0, backend.exported.get())
    }

    @Test
    fun theRendererSeesTheSanitisedSourceNotTheAuthors() {
        measure("""\textcolor{red}{-x}+\href{https://example.invalid}{go}""")
        val seen = backend.measured.single()
        assertFalse(seen.contains("red") || seen.contains("example.invalid") || seen.contains("href"), seen)
        assertTrue(seen.contains("−x"), seen)
    }

    @Test
    fun aSlowMeasurementIsAnsweredAsATimeoutAndRemembered() {
        backend.cost = MathLimits.CALL_BUDGET_MILLIS + 1
        assertEquals(MathMeasurement.Failed(MathFailure.TIMEOUT), measure("slow"))
        backend.cost = 0
        assertEquals(MathMeasurement.Failed(MathFailure.TIMEOUT), measure("slow"))
        assertEquals(1, backend.measured.size)
        assertEquals(MathMeasurement.Sized(120, 40), measure("quick"))
    }

    @Test
    fun aSlowDrawingMakesTheFormulaAFailureFromThenOn() {
        measure("a+b")
        backend.cost = MathLimits.CALL_BUDGET_MILLIS + 1
        assertNull(runBlocking { engine.render("a+b", 18f, 1f) })
        backend.cost = 0
        assertEquals(MathMeasurement.Failed(MathFailure.TIMEOUT), measure("a+b"))
        assertNull(runBlocking { engine.render("a+b", 18f, 1f) })
        assertEquals(1, backend.exported.get())
    }
}
