package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/** SYN-12: `$...$` inline, `$$...$$` display and fenced `math` display, recognised by the parser. */
class MathParseTest {
    private fun doc(md: String) = MarkdownParser.parse(md)

    private fun flat(inlines: List<Inline>): List<Inline> =
        inlines.flatMap { i ->
            when (i) {
                is Inline.Emphasis -> flat(i.children)
                is Inline.Strong -> flat(i.children)
                is Inline.Strikethrough -> flat(i.children)
                is Inline.Link -> flat(i.children)
                is Inline.Styled -> flat(i.children)
                else -> listOf(i)
            }
        }

    private fun contentsOf(block: Block): List<List<Inline>> =
        when (block) {
            is Paragraph -> listOf(block.content)
            is Heading -> listOf(block.content)
            is Table -> (listOf(block.header) + block.rows).flatMap { r -> r.cells.map { it.content } }
            else -> emptyList()
        }

    private fun inlineMath(md: String): List<Inline.InlineMath> =
        allBlocks(doc(md).blocks)
            .flatMap { b -> contentsOf(b).flatMap { flat(it) } }
            .filterIsInstance<Inline.InlineMath>()

    private fun texOf(md: String): List<String> = inlineMath(md).map { it.tex }

    private fun displayOf(md: String): List<MathBlock> = allBlocks(doc(md).blocks).filterIsInstance<MathBlock>()

    @Test
    fun `SYN-12 dollar pairs in prose are inline math with the exact source window`() {
        val md = "Energy is \$E = mc^2\$ here, and \$a_1 + b_2\$ too.\n"
        val math = inlineMath(md)
        assertEquals(listOf("E = mc^2", "a_1 + b_2"), math.map { it.tex })
        for (m in math) assertEquals(m.tex, md.substring(m.range!!.start, m.range!!.end))
        val p = doc(md).blocks.single() as Paragraph
        assertEquals("Energy is E = mc^2 here, and a_1 + b_2 too.", plainText(p.content))
    }

    @Test
    fun `emphasis markers inside a formula are not emphasis`() {
        assertEquals(listOf("a*b*c", "x_i_j"), texOf("\$a*b*c\$ and \$x_i_j\$\n"))
        val p = doc("*em \$a*b\$ em*\n").blocks.single() as Paragraph
        assertEquals(listOf("a*b"), flat(p.content).filterIsInstance<Inline.InlineMath>().map { it.tex })
    }

    @Test
    fun `currency is not math`() {
        for (md in listOf(
            "It costs \$5 and \$10 today.\n",
            "Between \$5-\$10.\n",
            "Prices: \$5/\$10.\n",
            "Only \$5.\n",
            "\$ 5 and 6 \$\n",
            "\$20,000 and \$30,000\n",
            "a \$ b\n",
        )) {
            assertEquals(emptyList<String>(), texOf(md), md)
            assertTrue(displayOf(md).isEmpty(), md)
            val p = doc(md).blocks.single() as Paragraph
            assertEquals(md.trimEnd('\n'), plainText(p.content), md)
        }
    }

    @Test
    fun `a closing dollar followed by a digit does not close`() {
        assertEquals(emptyList<String>(), texOf("\$x\$5\n"))
    }

    @Test
    fun `a digit right after the opener is allowed`() {
        assertEquals(listOf("2^n", "5"), texOf("\$2^n\$ and \$5\$\n"))
    }

    @Test
    fun `an escaped dollar is literal and stays inside a formula`() {
        assertEquals(emptyList<String>(), texOf("\\\$x\$ and more\n"))
        assertEquals(listOf("a\\\$b"), texOf("\$a\\\$b\$\n"))
        assertEquals(listOf("a\\\\"), texOf("\$a\\\\\$\n"))
        val p = doc("price \\\$5 and \\\$6\n").blocks.single() as Paragraph
        assertEquals("price \$5 and \$6", plainText(p.content))
    }

    @Test
    fun `an unclosed delimiter stays literal text`() {
        for (md in listOf("a \$x b\n", "a \$\$x b\n", "\$\$\n", "\$\$\$\$\n", "a \$\$\$ b\n")) {
            assertEquals(emptyList<String>(), texOf(md), md)
            assertTrue(displayOf(md).isEmpty(), md)
            val p = doc(md).blocks.single() as Paragraph
            assertEquals(md.trimEnd('\n'), plainText(p.content), md)
        }
    }

    @Test
    fun `a failed double dollar does not turn its second dollar into an opener`() {
        assertEquals(emptyList<String>(), texOf("\$\$x\$ y\n"))
    }

    @Test
    fun `inline math never spans paragraphs or lines`() {
        assertEquals(emptyList<String>(), texOf("a \$b\n\nc\$ d\n"))
        assertEquals(emptyList<String>(), texOf("a \$b\nc\$ d\n"))
        assertEquals(2, doc("a \$b\n\nc\$ d\n").blocks.size)
    }

    @Test
    fun `display math cannot span paragraphs either`() {
        val md = "\$\$\na\n\nb\n\$\$\n"
        assertTrue(displayOf(md).isEmpty())
        assertEquals(2, doc(md).blocks.size)
    }

    @Test
    fun `code spans and fences are untouched`() {
        assertEquals(emptyList<String>(), texOf("use `\$x\$` and `\$\$y\$\$` here\n"))
        val code = doc("```kotlin\nval a = \"\$x\$\"\n```\n").blocks.single() as CodeBlock
        assertEquals("val a = \"\$x\$\"", code.code)
        val indented = doc("    \$\$x\$\$\n").blocks.single()
        assertTrue(indented is CodeBlock)
        val mixed = inlineMath("`\$` then \$x\$ then `\$`\n")
        assertEquals(listOf("x"), mixed.map { it.tex })
    }

    @Test
    fun `a paragraph that is one double dollar pair becomes an atomic MathBlock with its range`() {
        val md = "Before.\n\n\$\$ \\frac{a}{b} \$\$\n\nAfter.\n"
        val d = doc(md)
        assertEquals(3, d.blocks.size)
        val block = d.blocks[1] as MathBlock
        assertEquals("\\frac{a}{b}", block.tex)
        assertEquals("\$\$ \\frac{a}{b} \$\$", md.substring(block.range.start, block.range.end))
    }

    @Test
    fun `a multi line double dollar paragraph becomes one MathBlock`() {
        val md = "\$\$\n\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}\n\$\$\n"
        val block = doc(md).blocks.single() as MathBlock
        assertEquals("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}", block.tex)
        assertEquals(0 until md.trimEnd().length, block.range.start until block.range.end)
    }

    @Test
    fun `double dollars in the middle of prose are inline math`() {
        val md = "Take \$\$x^2\$\$ now.\n"
        val p = doc(md).blocks.single() as Paragraph
        assertEquals(listOf("x^2"), flat(p.content).filterIsInstance<Inline.InlineMath>().map { it.tex })
        assertEquals("Take x^2 now.", plainText(p.content))
    }

    @Test
    fun `display math in a list item and a quote is promoted in place`() {
        val found = displayOf("- \$\$a\$\$\n\n> \$\$b\$\$\n")
        assertEquals(listOf("a", "b"), found.map { it.tex })
    }

    @Test
    fun `display math as part of a heading or text stays inline`() {
        assertTrue(displayOf("# \$\$x\$\$\n").isEmpty())
        assertEquals(listOf("x"), texOf("# \$\$x\$\$\n"))
        assertEquals(listOf("x"), texOf("see [\$x\$](u)\n"))
    }

    @Test
    fun `table cells hold inline math`() {
        val md = "| a | b |\n|---|---|\n| \$x\$ | \$\$y\$\$ |\n"
        assertEquals(listOf("x", "y"), texOf(md))
    }

    @Test
    fun `a fenced math block is a MathBlock and other tex fences stay code`() {
        val md = "```math\n\\int_0^1 x\\,dx\n```\n\n```latex\n\$x\$\n```\n"
        val d = doc(md)
        assertEquals(2, d.blocks.size)
        val math = d.blocks[0] as MathBlock
        assertEquals("\\int_0^1 x\\,dx", math.tex)
        assertEquals("latex", (d.blocks[1] as CodeBlock).language)
    }

    @Test
    fun `an empty fenced math block is an empty MathBlock and never throws`() {
        val d = doc("```math\n```\n")
        assertEquals("", (d.blocks.single() as MathBlock).tex)
    }

    @Test
    fun `math does not change the block count or the block ranges`() {
        val withMath = "Intro\n\n\$\$x\$\$\n\nMiddle \$y\$ text\n\n```math\nz\n```\n\nEnd\n"
        val plain = "Intro\n\nx\n\nMiddle y text\n\n```kotlin\nz\n```\n\nEnd\n"
        val a = doc(withMath)
        val b = doc(plain)
        assertEquals(b.blocks.size, a.blocks.size)
        // Same shapes, and each range stays inside its own region of the source.
        for ((i, block) in a.blocks.withIndex()) {
            assertTrue(block.range.end <= a.source.length, "$i")
            if (i > 0) assertTrue(block.range.start >= a.blocks[i - 1].range.end, "$i")
        }
    }

    @Test
    fun `inline math ranges are ordered inside their paragraph`() {
        val md = "> quoted \$a\$ and\n> \$b\$ lines\n\n- item \$c\$\n"
        val d = doc(md)
        for (m in inlineMath(md)) {
            val r = m.range!!
            assertEquals(m.tex, d.source.substring(r.start, r.end))
        }
        assertEquals(listOf("a", "b", "c"), texOf(md))
    }

    @Test
    fun `parsing a paragraph of many unmatched dollars is linear`() {
        val md = "\$5 ".repeat(100_000) + "\n"
        val started = System.nanoTime()
        val p = doc(md).blocks.single() as Paragraph
        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue(flat(p.content).none { it is Inline.InlineMath })
        assertTrue(ms < 5_000, "took $ms ms")
    }

    @Test
    @Timeout(20)
    fun `parsing a paragraph of many unclosed double dollars is linear`() {
        val md = "\$\$ a \$ ".repeat(50_000) + "\n"
        val p = doc(md).blocks.single() as Paragraph
        assertTrue(flat(p.content).isNotEmpty())
    }
}
