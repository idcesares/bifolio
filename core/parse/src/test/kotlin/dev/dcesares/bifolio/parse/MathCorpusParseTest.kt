package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Corpus C-14 and C-15 (SYN-12). The formulas are restated here on purpose, independent of the files. */
class MathCorpusParseTest {
    private val formulas =
        listOf(
            "E = mc^2",
            "a^2 + b^2 = c^2",
            "\\frac{a}{b}",
            "x_{i,j}^{2}",
            "\\sqrt{x^2+y^2}",
            "\\sqrt[3]{\\frac{a}{b}}",
            "\\alpha + \\beta = \\gamma",
            "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}",
            "\\int_0^\\infty e^{-x^2}\\,dx = \\frac{\\sqrt{\\pi}}{2}",
            "\\lim_{x\\to 0}\\frac{\\sin x}{x}=1",
            "x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}",
            "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
            "f(x)=\\begin{cases} x^2 & x\\ge 0 \\\\ -x & x<0 \\end{cases}",
            "\\hat{H}\\psi = i\\hbar\\frac{\\partial}{\\partial t}\\psi",
            "\\prod_{p\\ \\text{prime}} \\frac{1}{1-p^{-s}} = \\sum_{n=1}^\\infty \\frac{1}{n^s}",
            "\\nabla\\times\\vec{B} = \\mu_0\\vec{J}+\\mu_0\\varepsilon_0\\frac{\\partial\\vec{E}}{\\partial t}",
            "\\begin{aligned} (a+b)^2 &= a^2+2ab+b^2 \\\\ (a-b)^2 &= a^2-2ab+b^2 \\end{aligned}",
            "\\mathbb{R}\\subset\\mathbb{C},\\quad \\forall \\epsilon>0\\ \\exists\\delta>0",
            "\\left(\\sum_{k=1}^{n} a_k b_k\\right)^2 \\le \\left(\\sum_{k=1}^{n} a_k^2\\right)" +
                "\\left(\\sum_{k=1}^{n} b_k^2\\right)",
            "\\det\\begin{vmatrix} a_{11} & a_{12} & a_{13}\\\\ a_{21} & a_{22} & a_{23}\\\\ " +
                "a_{31} & a_{32} & a_{33}\\end{vmatrix}",
        )

    /** Whitespace is not significant in TeX math, and the files wrap long displays over lines. */
    private fun squash(s: String) = s.replace(Regex("\\s+"), "")

    private fun flat(inlines: List<Inline>): List<Inline> =
        inlines.flatMap { i ->
            when (i) {
                is Inline.Emphasis -> flat(i.children)
                is Inline.Strong -> flat(i.children)
                is Inline.Link -> flat(i.children)
                else -> listOf(i)
            }
        }

    @Test
    fun `C-14 has twenty formulas and every one is display math`() {
        assertEquals(20, formulas.size)
        val doc = Corpus.load("math-inline-display.md")
        val displays = allBlocks(doc.blocks).filterIsInstance<MathBlock>().map { squash(it.tex) }
        for ((i, f) in formulas.withIndex()) {
            assertTrue(displays.contains(squash(f)), "formula ${i + 1} not display: $f")
        }
    }

    @Test
    fun `C-14 mixes inline math into prose and keeps the sentences around it`() {
        val doc = Corpus.load("math-inline-display.md")
        val paragraphs = allBlocks(doc.blocks).filterIsInstance<Paragraph>()
        val inline = paragraphs.flatMap { flat(it.content) }.filterIsInstance<Inline.InlineMath>()
        val tex = inline.map { squash(it.tex) }
        assertTrue(inline.size >= 14, "inline pairs: ${inline.size}")
        for (f in formulas.take(8) + formulas[9] + formulas[11] + formulas[13] + formulas[17]) {
            assertTrue(tex.contains(squash(f)), "inline missing: $f")
        }
        val first = paragraphs.first { p -> flat(p.content).any { it is Inline.InlineMath } }
        assertTrue(plainText(first.content).startsWith("Einstein wrote E = mc^2 and the world changed."))
        for (m in inline) {
            val r = m.range!!
            assertEquals(m.tex, doc.source.substring(r.start, r.end))
        }
    }

    @Test
    fun `C-14 fenced math, a multi line display and the end of the file keep their text`() {
        val doc = Corpus.load("math-inline-display.md")
        val blocks = allBlocks(doc.blocks)
        assertTrue(blocks.none { it is CodeBlock && it.language == "math" })
        val cases = blocks.filterIsInstance<MathBlock>().first { "cases" in it.tex }
        assertTrue('\n' in cases.tex, "cases is written over several lines")
        val last = blocks.last() as Paragraph
        val text = plainText(last.content)
        assertTrue(text.contains("\$5 is a price"), text)
        assertEquals(listOf("a^2 + b^2 = c^2"), flat(last.content).filterIsInstance<Inline.InlineMath>().map { it.tex })
    }

    @Test
    fun `C-15 every failure input reaches the renderer untouched as a MathBlock`() {
        val doc = Corpus.load("math-errors.md")
        val tex = allBlocks(doc.blocks).filterIsInstance<MathBlock>().map { it.tex }
        val expected =
            listOf(
                "\\frac{a}",
                "\\unknowncmd{x} + 1",
                "\\begin{matrix} a & b",
                "\\end{pmatrix}",
                "}{",
                "x^",
                "\\left( x",
                "\\begin{pmatrix} a \\end{bmatrix}",
                "\\def\\a{\\a}\\a",
                "\\newcommand{\\a}{\\a\\a}\\a",
                "\\rule{100000em}{100000em}",
                "a\\hspace{99999999pt}b",
                "\\href{https://example.invalid/x}{link}",
                "\\textcolor{red}{x} + \\color{#00ff00} y",
            )
        for (e in expected) assertTrue(tex.contains(e), "missing failure input: $e")
        assertEquals(expected.size + 3, tex.size, "14 listed inputs plus 3 short stand-ins")
    }

    @Test
    fun `C-15 the delimiters that are not math stay text, inline and block`() {
        val doc = Corpus.load("math-errors.md")
        val blocks = allBlocks(doc.blocks)
        val paragraphs = blocks.filterIsInstance<Paragraph>()
        assertTrue(paragraphs.flatMap { flat(it.content) }.none { it is Inline.InlineMath })
        val text = paragraphs.joinToString("\n") { plainText(it.content) }
        for (s in listOf("\$5 and \$10", "\$5-\$10", "\$5/\$10", "\$20,000 and \$30,000", "\$x\$5", "\$x\$ is three")) {
            assertTrue(text.contains(s), "literal text missing: $s")
        }
        assertTrue(text.contains("sentence \$\$"), text)
        assertTrue(text.contains("\$\$\$\$"), text)
        assertTrue(blocks.filterIsInstance<CodeBlock>().any { "\$\$ not math \$\$" in it.code })
    }

    @Test
    fun `C-15 very long and deep inputs parse to one MathBlock each, without recursion`() {
        for (tex in listOf("{".repeat(10_000) + "x" + "}".repeat(10_000), "x" + "^{x".repeat(500) + "}".repeat(500)) +
            (1..5000).joinToString("+") { "x_{$it}" }) {
            val doc = MarkdownParser.parse("```math\n$tex\n```\n")
            assertEquals(tex, (doc.blocks.single() as MathBlock).tex)
            val inline = MarkdownParser.parse("a \$$tex\$ b\n").blocks.single() as Paragraph
            assertEquals(tex, flat(inline.content).filterIsInstance<Inline.InlineMath>().single().tex)
        }
    }
}
