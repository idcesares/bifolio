package dev.dcesares.bifolio.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** The pure guards of ADR-0006 (SYN-12, C-14, C-15): no renderer involved, so no Android either. */
class TexSanitizerTest {
    private fun ready(tex: String): String =
        (TexSanitizer.prepare(tex) as? PreparedTex.Ready)?.tex ?: error("rejected: $tex")

    private fun failure(tex: String): MathFailure =
        (TexSanitizer.prepare(tex) as? PreparedTex.Rejected)?.failure ?: error("accepted: ${tex.take(60)}")

    @Test
    fun theTwentyFormulasOfC14AllPassTheGuards() {
        for (tex in C14) {
            assertTrue(TexSanitizer.prepare(tex) is PreparedTex.Ready, tex)
        }
    }

    @Test
    fun emptyAndBlankAreRefused() {
        assertEquals(MathFailure.EMPTY, failure(""))
        assertEquals(MathFailure.EMPTY, failure("   "))
    }

    @Test
    fun malformedFormulasOfC15AreRefusedBeforeTheRenderer() {
        assertEquals(MathFailure.MISSING_OPERAND, failure("""\frac{a}"""))
        assertEquals(MathFailure.UNBALANCED, failure("""\begin{matrix} a & b"""))
        assertEquals(MathFailure.UNBALANCED, failure("""\end{pmatrix}"""))
        assertEquals(MathFailure.UNBALANCED, failure("}{"))
        assertEquals(MathFailure.MISSING_OPERAND, failure("x^"))
        assertEquals(MathFailure.MISSING_OPERAND, failure("x_"))
        assertEquals(MathFailure.UNBALANCED, failure("""\left( x"""))
        assertEquals(MathFailure.UNBALANCED, failure("""\begin{pmatrix} a \end{bmatrix}"""))
        assertEquals(MathFailure.UNBALANCED, failure("""\right) x"""))
        assertEquals(MathFailure.UNBALANCED, failure("{x"))
    }

    @Test
    fun anUnknownCommandIsLeftToTheParser() {
        // The renderer's parser keeps it as a node; the engine turns that into a failure.
        assertTrue(TexSanitizer.prepare("""\unknowncmd{x} + 1""") is PreparedTex.Ready)
    }

    @Test
    fun unbracedOperandChainsCountAgainstTheNestingLimit() {
        val deep = MathLimits.MAX_DEPTH + 1
        assertEquals(MathFailure.TOO_DEEP, failure("""\frac""".repeat(deep) + "{a}{b}"))
        assertEquals(MathFailure.TOO_DEEP, failure("""\sqrt""".repeat(399) + "x"))
        assertEquals(MathFailure.TOO_DEEP, failure("x" + "^".repeat(deep) + "x"))
        assertEquals(MathFailure.TOO_DEEP, failure("x" + "_^".repeat(deep) + "x"))
        assertEquals(MathFailure.TOO_DEEP, failure("""\frac \frac """.repeat(deep) + "{a}{b}"))
        // At the limit it is still a formula, and a group or a symbol between the operators starts a new chain.
        assertTrue(TexSanitizer.prepare("""\frac""".repeat(MathLimits.MAX_DEPTH) + "{a}{b}") is PreparedTex.Ready)
        assertTrue(TexSanitizer.prepare("x^a ".repeat(400) + "x") is PreparedTex.Ready)
        assertTrue(TexSanitizer.prepare("""\frac{a}{b}""".repeat(100)) is PreparedTex.Ready)
    }

    @Test
    fun macrosAreRefused() {
        assertEquals(MathFailure.MACRO, failure("""\def\a{\a}\a"""))
        assertEquals(MathFailure.MACRO, failure("""\newcommand{\a}{\a\a}\a"""))
        assertEquals(MathFailure.MACRO, failure("""\input{secrets}"""))
    }

    @Test
    fun hugeDimensionsAreRefused() {
        assertEquals(MathFailure.DIMENSION, failure("""\rule{100000em}{100000em}"""))
        assertEquals(MathFailure.DIMENSION, failure("""a\hspace{99999999pt}b"""))
        assertEquals(MathFailure.DIMENSION, failure("""a\kern 99999em b"""))
        assertEquals(MathFailure.DIMENSION, failure("""a\\[99999pt] b"""))
        assertEquals(MathFailure.DIMENSION, failure("""a\hspace{\textwidth}b"""))
        assertEquals(MathFailure.DIMENSION, failure("""x % comment"""))
    }

    @Test
    fun reasonableDimensionsAreKept() {
        assertEquals("""a\hspace{2em}b""", ready("""a\hspace{2em}b"""))
        assertEquals("""a\hspace{-1em}b""", ready("""a\hspace{-1em}b"""))
        assertEquals("""a\kern-3mu b""", ready("""a\kern-3mu b"""))
        assertEquals("""\rule{1em}{0.4pt}""", ready("""\rule{1em}{0.4pt}"""))
        assertEquals("""a\\[-2pt] b""", ready("""a\\[-2pt] b"""))
    }

    @Test
    fun deepAndLongSourceIsRefusedWithoutRecursing() {
        val braces = "{".repeat(10_000) + "x" + "}".repeat(10_000)
        assertEquals(MathFailure.TOO_LONG, failure(braces))
        val deep = "{".repeat(MathLimits.MAX_DEPTH + 1) + "x" + "}".repeat(MathLimits.MAX_DEPTH + 1)
        assertEquals(MathFailure.TOO_DEEP, failure(deep))
        val scripts = "x" + "^{x".repeat(500) + "}".repeat(500)
        assertTrue(TexSanitizer.prepare(scripts) is PreparedTex.Rejected)
        val sum = (1..5000).joinToString("+") { "x_{$it}" }
        assertEquals(MathFailure.TOO_LONG, failure(sum))
        assertTrue(
            TexSanitizer.prepare(
                "{".repeat(MathLimits.MAX_DEPTH) + "x" + "}".repeat(MathLimits.MAX_DEPTH),
            ) is PreparedTex.Ready,
        )
    }

    @Test
    fun theDeepSuperscriptStandInOfTheCorpusPasses() {
        val standIn = "x^{".repeat(20) + "x" + "}".repeat(20)
        assertTrue(TexSanitizer.prepare(standIn) is PreparedTex.Ready)
    }

    @Test
    fun authorColorsAreStrippedSoTheThemeForegroundAlwaysShows() {
        assertEquals("{x} + y", ready("""\textcolor{red}{x} + \color{#00ff00} y""").replace(Regex(" +"), " "))
        assertEquals("{\\frac{a}{b}}", ready("""\textcolor{#000000}{\frac{a}{b}}"""))
        assertEquals("{x}", ready("""\textcolor[HTML]{FF0000}{x}"""))
        assertEquals("{{x}}", ready("""\colorbox{yellow}{\textcolor{red}{x}}"""))
        assertEquals("{x}", ready("""\fcolorbox{red}{blue}{x}"""))
        assertEquals("x", ready("""\definecolor{mine}{rgb}{1,0,0}x"""))
        for (tex in listOf("""\textcolor{red}{x}""", """\color{#00ff00} y""", """\pagecolor{black}x""")) {
            val out = ready(tex)
            assertFalse(out.contains("color"), out)
        }
    }

    @Test
    fun linksAreInert() {
        val out = ready("""\href{https://example.invalid/x}{link}""")
        assertEquals("{link}", out)
        assertFalse(out.contains("http"))
        assertEquals("""\text{https://example.invalid/x}""", ready("""\url{https://example.invalid/x}"""))
    }

    @Test
    fun casesBecomesAnArrayInsideABrace() {
        val out = ready("""f(x)=\begin{cases} x^2 & x\ge 0 \\ -x & x<0 \end{cases}""")
        assertEquals(
            """f(x)=\left\{\begin{array}{ll} x^2 &\quad x\ge 0 \\ ${M}x &\quad x<0 \end{array}\right.""",
            out,
        )
        assertFalse(out.contains("cases"))
    }

    @Test
    fun casesKeepsNestedEnvironmentsAndCellsIntact() {
        val tex = """\begin{cases} \begin{pmatrix} a & b \end{pmatrix} & x \\ y & z \end{cases}"""
        val out = ready(tex)
        assertEquals(
            """\left\{\begin{array}{ll} \begin{pmatrix} a & b \end{pmatrix} """ +
                """&\quad x \\ y &\quad z \end{array}\right.""",
            out,
        )
    }

    @Test
    fun nestedCasesBothBecomeArrays() {
        val out = ready("""\begin{cases} \begin{cases} a & b \end{cases} & c \end{cases}""")
        assertFalse(out.contains("cases"))
        assertEquals(2, Regex("""\\begin\{array\}""").findAll(out).count())
    }

    @Test
    fun minusBecomesTheTrueMinusInMathOnly() {
        assertEquals("b^2${M}4ac", ready("b^2-4ac"))
        assertEquals("p^{${M}s}", ready("p^{-s}"))
        assertEquals("""\text{a-b} $M c""", ready("""\text{a-b} - c"""))
        assertEquals("""\mathrm{d-x} $M 1""", ready("""\mathrm{d-x} - 1"""))
        assertEquals("""\operatorname{arg-max}_x""", ready("""\operatorname{arg-max}_x"""))
        assertEquals("""\begin{array}{l|r} a \end{array}""", ready("""\begin{array}{l|r} a \end{array}"""))
        assertEquals("""\begin{array}{@{}l@{-}r} a \end{array}""", ready("""\begin{array}{@{}l@{-}r} a \end{array}"""))
    }

    @Test
    fun theUnrewrittenSourceIsWhatTheCallerKeeps() {
        // prepare never mutates its input and returns a new string; the caller shows the original on failure.
        val source = """\textcolor{red}{-x}"""
        assertEquals("{${M}x}", ready(source))
        assertEquals("""\textcolor{red}{-x}""", source)
    }

    @Test
    fun anyInputEndsInAnAnswerWithoutThrowing() {
        val random = Random(42)
        val alphabet =
            """\{}[]^_&$%-+ abcdxy0189.,begin endleftright frac sqrt """ +
                """textcolor href hspace rule cases array"""
        repeat(3_000) {
            val tex = String(CharArray(random.nextInt(0, 120)) { alphabet[random.nextInt(alphabet.length)] })
            TexSanitizer.prepare(tex)
        }
        val pieces =
            listOf(
                "\\frac",
                "{",
                "}",
                "\\begin{cases}",
                "\\end{cases}",
                "&",
                "\\\\",
                "^",
                "-",
                "\\left",
                "\\right.",
                "x",
            )
        repeat(3_000) {
            val tex = (0 until random.nextInt(0, 40)).joinToString("") { pieces[random.nextInt(pieces.size)] }
            TexSanitizer.prepare(tex)
        }
    }

    @Test
    fun anAcceptedFormulaIsAcceptedAgainAfterRewriting() {
        for (tex in C14) {
            val once = ready(tex)
            assertTrue(TexSanitizer.prepare(once) is PreparedTex.Ready, once)
        }
    }

    private companion object {
        const val M = "−"

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
