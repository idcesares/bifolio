package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.parse.MarkdownParser
import io.kotest.property.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ADR-0005: rendered characters map to source offsets through the inline source ranges, also where the rendered
 * text differs from the source (links, emphasis, code spans, entities, escapes).
 */
class SourceMappingTest {
    private fun rendered(md: String): Triple<Document, Rendered, Int> {
        val doc = MarkdownParser.parse(md)
        val block = doc.blocks.first()
        val inlines = (block as? Paragraph)?.content ?: (block as Heading).content
        return Triple(doc, RenderedText.of(inlines, doc.source, block.range.start, block.range.end), block.range.start)
    }

    private fun mapOf(md: String): Pair<String, IntArray> =
        rendered(md).let { (_, r, _) -> r.text to r.map.copyOf(r.text.length + 1) }

    @Test
    fun `text after a link does not match the link destination`() {
        val (text, map) = mapOf("[see](#see)see\n")
        assertEquals("seesee", text)
        assertEquals(listOf(1, 2, 3, 11, 12, 13), map.take(6))
    }

    @Test
    fun `an entity is one source window and later text stays aligned`() {
        val (text, map) = mapOf("&lt;lt\n")
        assertEquals("<lt", text)
        assertEquals(listOf(0, 4, 5), map.take(3))
    }

    @Test
    fun `an entity inside a sentence maps to its ampersand`() {
        val (text, map) = mapOf("a &amp; b &copy; c\n")
        assertEquals("a & b © c", text)
        assertEquals(listOf(0, 1, 2, 7, 8, 9, 10, 16, 17), map.take(9))
    }

    @Test
    fun `an escape maps to its backslash and the character after it is not skipped`() {
        val (text, map) = mapOf("\\*a\\*a\n")
        assertEquals("*a*a", text)
        assertEquals(listOf(0, 2, 3, 5), map.take(4))
    }

    @Test
    fun `an escaped star inside strong text stays inside the strong window`() {
        val (text, map) = mapOf("**bold \\*x\\*** end\n")
        assertEquals("bold *x* end", text)
        assertEquals(2, map[0])
        assertEquals(7, map[5])
        assertEquals(9, map[6])
        assertEquals(14, map[8])
    }

    @Test
    fun `a code span before a link keeps later text out of the link destination`() {
        val (text, map) = mapOf("`ab`[ab](ab)ab\n")
        assertEquals("ababab", text)
        assertEquals(listOf(1, 2, 5, 6, 12, 13), map.take(6))
    }

    @Test
    fun `an image alt followed by text`() {
        val (text, map) = mapOf("![k](k.png)k\n")
        assertEquals("kk", text)
        assertEquals(listOf(2, 11), map.take(2))
    }

    @Test
    fun `soft and hard breaks sit between the neighbouring ranges`() {
        val (text, map) = mapOf("one\ntwo  \nthree\n")
        assertEquals("one\ntwo\nthree", text)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 9, 10), map.take(9))
        assertEquals(15, map[text.length])
    }

    @Test
    fun `the map is non decreasing, in bounds and has one entry per character plus the end`() {
        val (doc, r, start) = rendered("# T &amp; [x](y) `c` \\* **b _i_** &#128512; z\n")
        assertEquals(r.text.length + 1, r.map.size.coerceAtLeast(r.text.length + 1))
        val end =
            doc.blocks
                .first()
                .range.end
        for (i in 1..r.text.length) assertTrue(r.map[i] >= r.map[i - 1], "decreasing at $i")
        for (i in 0..r.text.length) assertTrue(r.map[i] in start..end, "out of bounds at $i")
    }

    @Test
    fun `an astral entity occupies two rendered chars and keeps later text aligned`() {
        val (text, map) = mapOf("&#128512; z\n")
        assertEquals("😀 z", text)
        assertEquals(listOf(0, 0, 9, 10), map.take(4))
    }

    @Test
    fun `code block lines map to their own source lines through container prefixes`() {
        val doc = MarkdownParser.parse("> ```\n> ab\n>   cd\n> ```\n")
        val quote = doc.blocks.single() as BlockQuote
        val code = quote.blocks.single() as CodeBlock
        val from = doc.source.indexOf('\n', code.range.start) + 1
        val map = CodeSourceMap.build(code.code, doc.source, from, code.range.end)
        assertEquals("ab\n  cd", code.code)
        assertEquals(doc.source.indexOf("ab"), map[0])
        assertEquals(doc.source.indexOf("cd") - 2, map[3])
        assertEquals(doc.source.indexOf("cd"), map[5])
    }

    @Test
    fun `inlines without a range fall back to a greedy match and stay monotone`() {
        val source = "**bold** and `code` tail"
        val inlines =
            listOf(
                Inline.Text("bold"),
                Inline.Text(" and "),
                Inline.Code("code"),
                Inline.Text(" tail"),
            )
        val r = RenderedText.of(inlines, source, 0, source.length)
        assertEquals("bold and code tail", r.text)
        assertEquals(2, r.map[0])
        assertEquals(source.indexOf("code"), r.map[9])
        assertEquals(source.indexOf("tail"), r.map[14])
        for (i in 1..r.text.length) assertTrue(r.map[i] >= r.map[i - 1])
    }

    @Test
    fun `indented and plain fenced code map line by line`() {
        val doc = MarkdownParser.parse("    ab\n    cd\n")
        val indented = doc.blocks.single() as CodeBlock
        val m1 = CodeSourceMap.build(indented.code, doc.source, indented.range.start, indented.range.end)
        assertEquals(listOf(4, 5, 6, 11, 12), m1.take(5))
        val fenced = MarkdownParser.parse("```\nx\n\ny\n```\n")
        val code = fenced.blocks.single() as CodeBlock
        val m2 = CodeSourceMap.build(code.code, fenced.source, 4, code.range.end)
        assertEquals("x\n\ny", code.code)
        assertEquals(listOf(4, 5, 6, 7, 8), m2.take(5))
    }

    // End to end: line -> source offset -> anchor -> same page (ANC-03, PAG-11)

    private val inlineHeavy =
        listOf(
            "Plain words with *emphasis* and **strong text** and `code spans` inside the line.",
            "A [link text](http://example.com/some/long/destination) then &amp; entity &lt;b&gt; and \\*escaped\\*.",
            "![alt text](picture.png) after an image, `x` `y` `zz` and [a](a)a[b](b)b.",
            "Hard  \nbreak and soft\nbreak with ~~strike~~ and <kbd>K</kbd> and <u>under</u> &copy; end.",
            "# Heading with `code` and *em* &amp; more",
            "> quoted [link](u) with `code` and &amp; entity and enough words to wrap a few lines",
            "- item with **bold** and [l](u) and `c`, long enough to wrap around the column\n- second &lt;item&gt;",
        )

    private fun lineAnchors(
        doc: Document,
        flattener: BlockFlattener,
        width: Int,
    ): List<Triple<Int, Int, Anchor>> {
        val out = ArrayList<Triple<Int, Int, Anchor>>()
        for (b in doc.blocks.indices) {
            val layout = flattener.layout(b, width)
            for (l in 0 until layout.lineCount) out.add(Triple(b, l, Anchor(b, layout.srcStart[l])))
        }
        return out
    }

    private fun checkRoundTrip(
        md: String,
        columns: Int,
        lines: Int,
        columnWidth: Int,
        label: String,
    ) {
        val doc = MarkdownParser.parse(md)
        val geometry = testGeometry(lines = lines, columns = columns, columnWidth = columnWidth)
        val flow = Paginator(doc, FakeBlockMeasurer(), geometry).paginateAll()!!
        val flattener = BlockFlattener(doc, FakeBlockMeasurer(), geometry)
        val width = geometry.columnWidths[0]
        for ((b, line, anchor) in lineAnchors(doc, flattener, width)) {
            val pageOfLine =
                flow.pages.indexOfFirst { p ->
                    p.slices.any {
                        it.blockIndex == b &&
                            line >= it.startLine &&
                            line < it.endLine
                    }
                }
            assertTrue(pageOfLine >= 0, "$label: line $b:$line is on no page")
            assertEquals(pageOfLine, flow.pageIndexOf(anchor), "$label: anchor $anchor of line $b:$line")
        }
        val starts = lineAnchors(doc, flattener, width).groupBy { it.first }
        for ((b, entries) in starts) {
            val offsets = entries.map { it.third.charOffset }
            assertEquals(
                offsets.sorted().distinct(),
                offsets,
                "$label: block $b line starts are not strictly increasing",
            )
        }
    }

    @Test
    fun `line to source offset to anchor lands on the same page for inline heavy text`() {
        val md = markdown(*inlineHeavy.toTypedArray())
        for (columnWidth in listOf(100, 200, 300)) {
            checkRoundTrip(md, columns = 1, lines = 4, columnWidth = columnWidth, label = "w$columnWidth")
            checkRoundTrip(md, columns = 2, lines = 6, columnWidth = columnWidth, label = "w$columnWidth x2")
        }
    }

    @Test
    fun `random inline heavy documents round trip at random geometries`() {
        val rs = RandomSource.seeded(20261017L)
        repeat(150) { i ->
            val count = rs.random.nextInt(1, 9)
            val md = markdown(*Array(count) { inlineHeavy[rs.random.nextInt(inlineHeavy.size)] })
            val columns = rs.random.nextInt(1, 4)
            checkRoundTrip(md, columns, rs.random.nextInt(2, 12), rs.random.nextInt(8, 31) * 10, "doc $i")
        }
    }

    @Test
    fun `a line start maps to the source character it renders where the text is plain`() {
        val md = markdown("aaaa bbbb *cccc* dddd [eeee](u) ffff &amp; gggg hhhh")
        val doc = MarkdownParser.parse(md)
        val geometry = testGeometry(lines = 10, columnWidth = 100)
        val layout = BlockFlattener(doc, FakeBlockMeasurer(), geometry).layout(0, 100)
        val block = doc.blocks.first()
        val firsts = (0 until layout.lineCount).map { doc.source[block.range.start + layout.srcStart[it]] }
        assertTrue(layout.lineCount >= 3)
        assertEquals('a', firsts.first())
        assertTrue(firsts.all { it.isLetterOrDigit() || it == '&' }, "line starts on markup: $firsts")
    }
}
