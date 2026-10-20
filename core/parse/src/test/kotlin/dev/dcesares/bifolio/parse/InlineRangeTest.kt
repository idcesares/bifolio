package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.SourceRange
import dev.dcesares.bifolio.model.Table
import io.kotest.property.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR-0005: every inline that renders text exposes the source window of that text. */
class InlineRangeTest {
    private fun inlinesOf(md: String): List<Inline> = (MarkdownParser.parse(md).blocks.first() as Paragraph).content

    private fun leaves(inlines: List<Inline>): List<Inline> =
        inlines.flatMap { i ->
            when (i) {
                is Inline.Emphasis -> leaves(i.children)
                is Inline.Strong -> leaves(i.children)
                is Inline.Strikethrough -> leaves(i.children)
                is Inline.Link -> leaves(i.children)
                is Inline.Styled -> leaves(i.children)
                else -> listOf(i)
            }
        }

    private fun rangeOf(i: Inline): SourceRange? =
        when (i) {
            is Inline.Text -> i.range
            is Inline.Code -> i.range
            is Inline.Image -> i.range
            is Inline.InlineMath -> i.range
            else -> null
        }

    private fun slice(
        md: String,
        i: Inline,
    ): String = rangeOf(i)!!.let { MarkdownParser.parse(md).source.substring(it.start, it.end) }

    @Test
    fun `plain text covers exactly its characters`() {
        val md = "hello world\n"
        assertEquals(listOf(Inline.Text("hello world", SourceRange(0, 11))), inlinesOf(md))
    }

    @Test
    fun `entities and escapes keep the whole source window`() {
        val md = "a &amp; b &lt;b&gt; \\* c\n"
        val text = inlinesOf(md).single() as Inline.Text
        assertEquals("a & b <b> * c", text.text)
        assertEquals(SourceRange(0, 24), text.range)
    }

    @Test
    fun `code span range excludes the backtick delimiters`() {
        val md = "x `a` y ``b ` c`` z `` pad `` w\n"
        val codes = leaves(inlinesOf(md)).filterIsInstance<Inline.Code>()
        assertEquals(listOf("a", "b ` c", "pad"), codes.map { it.text })
        assertEquals(listOf("a", "b ` c", " pad "), codes.map { slice(md, it) })
    }

    @Test
    fun `link and emphasis children carry their own windows`() {
        val md = "[see](#see) see *em*\n"
        val ls = leaves(inlinesOf(md))
        assertEquals(listOf(SourceRange(1, 4), SourceRange(11, 16), SourceRange(17, 19)), ls.map(::rangeOf))
        assertEquals(listOf("see", " see ", "em"), ls.map { slice(md, it) })
    }

    @Test
    fun `image alt window is the whole image syntax`() {
        val md = "![alt *e*](u.png) after\n"
        val img = inlinesOf(md).first() as Inline.Image
        assertEquals("alt e", img.alt)
        assertEquals(SourceRange(0, 17), img.range)
    }

    @Test
    fun `fallback HTML merges into the surrounding text with the union range`() {
        val md = "x <foo> y\n"
        val text = inlinesOf(md).single() as Inline.Text
        assertEquals("x <foo> y", text.text)
        assertEquals(SourceRange(0, 9), text.range)
    }

    @Test
    fun `breaks have no range and text on both sides keeps its own`() {
        val md = "one\ntwo  \nthree\n"
        val p = inlinesOf(md)
        assertEquals(listOf(Inline.SoftBreak, Inline.HardBreak), p.filter { it !is Inline.Text })
        val texts = p.filterIsInstance<Inline.Text>()
        assertEquals(listOf(SourceRange(0, 3), SourceRange(4, 9), SourceRange(10, 15)), texts.map { it.range })
        assertEquals(listOf("one", "two", "three"), texts.map { it.text })
    }

    @Test
    fun `container prefixes are not part of the window`() {
        val md = "> quoted *em* line\n> second &amp; line\n"
        val quote = MarkdownParser.parse(md).blocks.single() as dev.dcesares.bifolio.model.BlockQuote
        val ls = leaves((quote.blocks.single() as Paragraph).content)
        assertEquals(SourceRange(21, 38), rangeOf(ls.last()))
        val list = MarkdownParser.parse("- item one\n  continues &copy; here\n- two\n").blocks.single() as ListBlock
        val p = list.items[0].blocks.single() as Paragraph
        assertEquals(SourceRange(13, 34), rangeOf(leaves(p.content).last()))
    }

    @Test
    fun `table cell content has its window`() {
        val md = "| a | b &amp; |\n|---|---|\n| *c* | d |\n"
        val t = MarkdownParser.parse(md).blocks.single() as Table
        assertEquals(
            SourceRange(6, 13),
            rangeOf(
                t.header.cells[1]
                    .content
                    .single(),
            ),
        )
        assertEquals(
            SourceRange(34, 35),
            rangeOf(
                t.rows[0]
                    .cells[1]
                    .content
                    .single(),
            ),
        )
    }

    @Test
    fun `summary inlines are shifted to document offsets`() {
        val md = "<details>\n<summary>Hello *x*</summary>\n\nBody\n\n</details>\n"
        val d = MarkdownParser.parse(md).blocks.first() as HtmlDetails
        val ls = leaves(d.summary)
        assertEquals(listOf("Hello ", "x"), ls.map { (it as Inline.Text).text })
        assertEquals(listOf("Hello ", "x"), ls.map { slice(md, it) })
    }

    @Test
    fun `HTML block lines found in the source get exact windows`() {
        val md = "<div>raw</div>\n"
        val p = MarkdownParser.parse(md).blocks.single() as Paragraph
        assertEquals(SourceRange(0, 14), rangeOf(p.content.single()))
    }

    private fun checkBlock(
        doc: String,
        block: Block,
        label: String,
    ) {
        val content: List<Inline>? =
            when (block) {
                is Paragraph -> block.content
                is Heading -> block.content
                else -> null
            }
        if (content != null) {
            var cursor = block.range.start
            for (leaf in leaves(content)) {
                val r = rangeOf(leaf) ?: continue
                assertTrue(r.start >= cursor, "$label: $leaf starts at ${r.start} before $cursor")
                assertTrue(r.end <= block.range.end, "$label: $leaf ends past its block ${block.range}")
                assertTrue(r.end <= doc.length, "$label: $leaf past the document")
                cursor = r.end
            }
        }
        if (block is HtmlDetails) {
            for (leaf in leaves(block.summary)) {
                val r = rangeOf(leaf) ?: continue
                assertTrue(r.start in block.range.start..block.range.end, "$label: summary outside ${block.range}")
            }
        }
    }

    @Test
    fun `corpus ranges are ordered and inside their blocks`() {
        for (name in Corpus.names()) {
            val doc = Corpus.load(name)
            for (b in allBlocks(doc.blocks)) checkBlock(doc.source, b, name)
        }
    }

    @Test
    fun `text and heading leaves of the corpus carry ranges`() {
        for (name in Corpus.names()) {
            val doc = Corpus.load(name)
            val contents = allBlocks(doc.blocks).mapNotNull { (it as? Paragraph)?.content ?: (it as? Heading)?.content }
            for (leaf in contents.flatMap { leaves(it) }) {
                if (leaf is Inline.Text || leaf is Inline.Code) assertNotNull(rangeOf(leaf), "$name: $leaf")
            }
        }
    }

    @Test
    fun `random documents keep inline ranges ordered and inside their blocks`() {
        val fragments =
            listOf(
                "# Heading *x*",
                "Para with **bold** and `code` and &amp; entity \\* escape.",
                "> quote with [link](u)",
                "- item\n  more text",
                "```\ncode\n```",
                "<details>\n<summary>S *x*</summary>\n\nBody\n\n</details>",
                "<div>raw</div>",
                "text<br>more <kbd>K</kbd> <sub>s</sub>",
                "![img](a.png) after",
                "a\nb  \nc\\\nd",
            )
        val rs = RandomSource.seeded(7L)
        repeat(300) { i ->
            val md =
                List(
                    rs.random.nextInt(1, 12),
                ) { fragments[rs.random.nextInt(fragments.size)] }.joinToString("\n\n")
            val doc = MarkdownParser.parse(md)
            for (b in allBlocks(doc.blocks)) checkBlock(doc.source, b, "doc $i")
        }
    }
}
