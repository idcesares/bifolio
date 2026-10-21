package dev.dcesares.bifolio.math

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.export.rememberLatexExporter
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The renderer behind the adapter on a real (Robolectric) canvas: ADR-0006 guards 1, 4, 5, 8, 9 and the C-14 set. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class MathEngineTest {
    @get:Rule val compose = createComposeRule()

    private var engine: MathEngine? = null
    private var bareEngine: MathEngine? = null
    private var density by mutableStateOf(Density(2.625f, 1f))
    private var started = false

    private fun engine(fontScale: Float = 1f): MathEngine {
        density = Density(2.625f, fontScale)
        if (!started) {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides density) {
                    Capture()
                    CaptureBare()
                }
            }
            started = true
        }
        compose.waitForIdle()
        return checkNotNull(engine)
    }

    @Composable
    private fun Capture() {
        engine = rememberMathEngine()
    }

    /** The same library with no fonts handed to it: what a first measurement would see without the injection. */
    @Composable
    private fun CaptureBare() {
        val config = remember { LatexConfig(fontSize = FONT_SP.sp, accessibilityEnabled = false) }
        val measurer = rememberLatexMeasurer(config)
        val exporter = rememberLatexExporter(config)
        val d = LocalDensity.current
        bareEngine = MathEngine(LibraryBackend(measurer, exporter, config), d.density, d.fontScale)
    }

    private fun sized(m: MathMeasurement): MathMeasurement.Sized = m as MathMeasurement.Sized

    @Test
    fun everyFormulaOfC14IsMeasuredAndDrawnWithTheMeasuredSize() {
        val e = engine()
        val sheet = mutableListOf<Bitmap>()
        for (tex in C14) {
            val size = sized(runBlocking { e.measure(tex, FONT_SP) })
            assertTrue(tex, size.width > 0 && size.height > 0)
            val bitmap = runBlocking { e.render(tex, FONT_SP, 1f) }
            assertNotNull(tex, bitmap)
            assertTrue(
                "$tex ${bitmap!!.width}x${bitmap.height} vs $size",
                kotlin.math.abs(bitmap.width - size.width) <= 2,
            )
            assertTrue("$tex ${bitmap.height} vs ${size.height}", kotlin.math.abs(bitmap.height - size.height) <= 2)
            sheet += bitmap
        }
        writeSheet("math-c14.png", sheet)
    }

    @Test
    fun scaleShrinksTheBitmapAndNeverEnlargesIt() {
        val e = engine()
        val tex = """\sum_{i=1}^{n} i = \frac{n(n+1)}{2}"""
        val full = runBlocking { e.render(tex, FONT_SP, 1f) }!!
        val half = runBlocking { e.render(tex, FONT_SP, 0.5f) }!!
        val over = runBlocking { e.render(tex, FONT_SP, 3f) }!!
        assertTrue(half.width in (full.width / 2 - 2)..(full.width / 2 + 2))
        assertEquals(full.width, over.width)
    }

    @Test
    fun malformedAndHostileInputsFailWithoutThrowing() {
        val e = engine()
        val expected =
            mapOf(
                """\frac{a}""" to MathFailure.MISSING_OPERAND,
                """\begin{matrix} a & b""" to MathFailure.UNBALANCED,
                """\def\a{\a}\a""" to MathFailure.MACRO,
                """\rule{100000em}{100000em}""" to MathFailure.DIMENSION,
                """a\hspace{99999999pt}b""" to MathFailure.DIMENSION,
                "{".repeat(10_000) + "x" + "}".repeat(10_000) to MathFailure.TOO_LONG,
            )
        for ((tex, failure) in expected) {
            assertEquals(tex.take(40), MathMeasurement.Failed(failure), runBlocking { e.measure(tex, FONT_SP) })
            assertNull(runBlocking { e.render(tex, FONT_SP, 1f) })
        }
        val unknown = runBlocking { e.measure("""\unknowncmd{x} + 1""", FONT_SP) }
        assertTrue(unknown.toString(), unknown is MathMeasurement.Failed)
    }

    @Test
    fun theLargestAcceptedSourcesEndInTheExpectedAnswer() {
        val e = engine()
        val deepest = "x^{".repeat(MathLimits.MAX_DEPTH - 2) + "x" + "}".repeat(MathLimits.MAX_DEPTH - 2)
        val longest = (1..150).joinToString("+") { "x_{$it}" }
        val answers = listOf(deepest, longest, """\rule{20em}{20em}""").map { measure(e, it) }
        println("LARGEST $answers")
        assertTrue(answers[0].toString(), answers[0] is MathMeasurement.Sized)
        assertEquals(MathMeasurement.Failed(MathFailure.SIZE), answers[1])
        assertTrue(answers[2].toString(), answers[2] is MathMeasurement.Sized)
    }

    @Test
    fun unbracedChainsEndAsFailuresQuickly() {
        val e = engine()
        val chains =
            listOf(
                """\frac""".repeat(300) + "{a}{b}",
                """\sqrt""".repeat(399) + "x",
                "x" + "^".repeat(1_000),
                "x" + "_^".repeat(500),
            )
        for (tex in chains) {
            val started = System.nanoTime()
            val answer = measure(e, tex)
            val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
            println("CHAIN ${tex.take(12)} $answer ${millis}ms")
            assertTrue("$answer for ${tex.take(20)}", answer is MathMeasurement.Failed)
            assertTrue("$millis ms for ${tex.take(20)}", millis < MathLimits.CALL_BUDGET_MILLIS)
        }
    }

    @Test
    fun includegraphicsAndFileCommandsEndAsFailuresAndNeverDraw() {
        val e = engine()
        val sources =
            listOf(
                """\includegraphics{a.png}""",
                """\includegraphics[width=1cm]{http://example.invalid/a.png}""",
                """\input{x}""",
            )
        for (tex in sources) {
            assertTrue(tex, measure(e, tex) is MathMeasurement.Failed)
            assertNull(tex, runBlocking { e.render(tex, FONT_SP, 1f) })
        }
    }

    @Test
    fun aLinkIsOnlyItsTextAndAnUrlIsOnlyPlainText() {
        val e = engine()
        assertEquals(measure(e, "{link text}"), measure(e, """\href{https://example.invalid/secret}{link text}"""))
        assertEquals(
            measure(e, """\text{https://example.invalid/x}"""),
            measure(e, """\url{https://example.invalid/x}"""),
        )
        assertNotNull(runBlocking { e.render("""\href{https://example.invalid}{a}""", FONT_SP, 1f) })
    }

    @Test
    fun theKatexFontsAreInPlaceBeforeTheFirstMeasurement() {
        val e = engine()
        val bare = checkNotNull(bareEngine)
        var differing = 0
        for (tex in C14) {
            val first = measure(e, tex)
            // The first answer equals a later one: no font arrived late.
            assertEquals(tex, first, runBlocking { e.remeasure(tex, FONT_SP) })
            if (first != measure(bare, tex)) differing++
        }
        // Without the injected fonts the renderer measures other glyphs (ADR-0006 found 20 of 20 different).
        assertTrue("only $differing of ${C14.size} widths depend on the fonts", differing >= C14.size / 2)
        val assets = RuntimeEnvironment.getApplication().assets
        val bytes = assets.open(KatexFonts.ROOT + "katex_main_regular.ttf").use { it.readBytes() }
        assertTrue(bytes.size > MIN_FONT_BYTES)
    }

    private fun measure(
        e: MathEngine,
        tex: String,
    ): MathMeasurement = runBlocking { e.measure(tex, FONT_SP) }

    @Test
    fun authorColorsDoNotReachThePixels() {
        val e = engine()
        val bitmap = runBlocking { e.render("""\textcolor{red}{x} + \color{#00ff00} y""", FONT_SP, 1f) }!!
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val inked = pixels.filter { Color.alpha(it) > 128 }
        assertTrue(inked.isNotEmpty())
        assertTrue(inked.all { Color.red(it) < 40 && Color.green(it) < 40 && Color.blue(it) < 40 })
    }

    @Test
    fun theTextScaleGrowsTheFormulaAndTheEngineFollowsIt() {
        val tex = """\frac{a}{b}"""
        val normal = sized(runBlocking { engine().measure(tex, FONT_SP) })
        val big = engine(fontScale = 2f)
        assertEquals(2f, big.fontScale)
        val large = sized(runBlocking { big.measure(tex, FONT_SP) })
        assertTrue("$normal -> $large", large.height > normal.height * 1.6f)
    }

    @Test
    fun casesMinusAndArraySheetForInspection() {
        val e = engine()
        val formulas =
            listOf(
                """f(x)=\begin{cases} x^2 & x\ge 0 \\ -x & x<0 \end{cases}""",
                """x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}""",
                """p^{-s}\quad a-b\quad \text{a-b}""",
                """\textcolor{red}{x}+\href{https://example.invalid}{link}""",
            )
        writeSheet("math-rewrites.png", formulas.map { runBlocking { e.render(it, FONT_SP, 1f) }!! })
    }

    private fun writeSheet(
        name: String,
        bitmaps: List<Bitmap>,
    ) {
        val pad = 16
        val width = (bitmaps.maxOfOrNull { it.width } ?: 1) + pad * 2
        val height = bitmaps.sumOf { it.height + pad } + pad
        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        var y = pad
        for (b in bitmaps) {
            canvas.drawBitmap(b, pad.toFloat(), y.toFloat(), Paint())
            y += b.height + pad
        }
        val dir = File("build/math").also { it.mkdirs() }
        File(dir, name).outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val FONT_SP = 18f
        const val NANOS_PER_MILLI = 1_000_000L
        const val MIN_FONT_BYTES = 1_000
        val C14 =
            listOf(
                """E = mc^2""",
                """a^2 + b^2 = c^2""",
                """\frac{a}{b}""",
                """x_{i,j}^{2}""",
                """\sqrt{x^2+y^2}""",
                """\sqrt[3]{\frac{a}{b}}""",
                """\alpha + \beta = \gamma""",
                """\sum_{i=1}^{n} i = \frac{n(n+1)}{2}""",
                """\int_0^\infty e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}""",
                """\lim_{x\to 0}\frac{\sin x}{x}=1""",
                """x = \frac{-b \pm \sqrt{b^2-4ac}}{2a}""",
                """\begin{pmatrix} a & b \\ c & d \end{pmatrix}""",
                """f(x)=\begin{cases} x^2 & x\ge 0 \\ -x & x<0 \end{cases}""",
                """\hat{H}\psi = i\hbar\frac{\partial}{\partial t}\psi""",
                """\prod_{p\ \text{prime}} \frac{1}{1-p^{-s}} = \sum_{n=1}^\infty \frac{1}{n^s}""",
                """\nabla\times\vec{B} = \mu_0\vec{J}+\mu_0\varepsilon_0\frac{\partial\vec{E}}{\partial t}""",
                """\begin{aligned} (a+b)^2 &= a^2+2ab+b^2 \\ (a-b)^2 &= a^2-2ab+b^2 \end{aligned}""",
                """\mathbb{R}\subset\mathbb{C},\quad \forall \epsilon>0\ \exists\delta>0""",
                """\left(\sum_{k=1}^{n} a_k b_k\right)^2 \le \left(\sum_{k=1}^{n} """ +
                    """a_k^2\right)\left(\sum_{k=1}^{n} b_k^2\right)""",
                """\det\begin{vmatrix} a_{11} & a_{12} & a_{13}\\ a_{21} & a_{22} & """ +
                    """a_{23}\\ a_{31} & a_{32} & a_{33}\end{vmatrix}""",
            )
    }
}
