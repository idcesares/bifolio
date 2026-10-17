package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.ColumnAlignment
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.InlineStyle
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.TaskState
import dev.dcesares.bifolio.model.ThematicBreak
import dev.dcesares.bifolio.model.nestingDepth
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownParserTest {
    private fun parse(md: String): Document = MarkdownParser.parse(md)

    private fun headings(doc: Document) = allBlocks(doc.blocks).filterIsInstance<Heading>()

    @Test
    fun `SYN-01 ATX and Setext headings of levels 1 to 6`() {
        val doc = Corpus.load("headings-all-levels.md")
        val hs = headings(doc)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1, 2, 2, 2, 2), hs.map { it.level })
        assertEquals("Setext one", plainText(hs[6].content))
        assertEquals("Setext two", plainText(hs[7].content))
    }

    @Test
    fun `SYN-01 slugs are GitHub style and duplicates get numeric suffixes`() {
        val hs = headings(Corpus.load("headings-all-levels.md"))
        assertEquals(
            listOf("level-one", "level-two", "level-three", "level-four", "level-five", "level-six"),
            hs.take(6).map { it.slug },
        )
        assertEquals(listOf("repeated", "repeated-1", "repeated-2"), hs.takeLast(3).map { it.slug })
    }

    @Test
    fun `SYN-01 slugs keep non-Latin letters and accents (corpus C-04)`() {
        val slugs = headings(Corpus.load("headings-non-latin.md")).map { it.slug }
        assertEquals(
            listOf(
                "introdução-à-leitura",
                "ação-e-coração",
                "ελληνικά-κείμενα",
                "привет-мир",
                "日本語の見出し",
                "中文标题",
                "introdução-à-leitura-1",
                "mixed-c--rust-2026",
            ),
            slugs,
        )
    }

    @Test
    fun `SYN-01 a natural slug colliding with a generated suffix stays unique`() {
        val slugs = headings(parse("# a\n\n# a\n\n# a-1\n\n# a\n")).map { it.slug }
        assertEquals(4, slugs.toSet().size)
        assertEquals(listOf("a", "a-1", "a-1-1", "a-2"), slugs)
    }

    @Test
    fun `SYN-01 slug ignores emphasis and keeps code text`() {
        val h = headings(parse("## The *quick* `fn()` fox\n")).single()
        assertEquals("the-quick-fn-fox", h.slug)
    }

    @Test
    fun `DOC-06 title is the first level one heading, null when absent`() {
        assertEquals("A short walk", Corpus.load("prose-short.md").title)
        assertNull(parse("just text\n").title)
    }

    @Test
    fun `SYN-02 emphasis strong strike code links and breaks (corpus C-01)`() {
        val doc = Corpus.load("prose-short.md")
        val paras = doc.blocks.filterIsInstance<Paragraph>()
        val first = paras[0].content
        assertTrue(first.any { it is Inline.Emphasis && plainText(it.children) == "river" })
        assertTrue(first.any { it is Inline.Strong && plainText(it.children) == "small notebook" })
        val third = paras[2].content
        assertTrue(third.any { it is Inline.Strikethrough && plainText(it.children) == "a mistake" })
        assertTrue(third.any { it is Inline.Code && it.text == "inline code" })
        assertTrue(third.contains(Inline.SoftBreak))
        assertTrue(third.contains(Inline.HardBreak))
    }

    @Test
    fun `SYN-07 links autolinks and reference links`() {
        val paras = Corpus.load("prose-short.md").blocks.filterIsInstance<Paragraph>()
        val inline = paras[1].content.filterIsInstance<Inline.Link>()
        assertEquals("https://example.org/archive", inline[0].destination)
        assertEquals("town archive", plainText(inline[0].children))
        assertEquals("https://example.org/harbour", inline[1].destination)
        val ref =
            paras
                .last()
                .content
                .filterIsInstance<Inline.Link>()
                .single()
        assertEquals("https://example.org/harbour", ref.destination)
        assertEquals("Harbour records", ref.title)
        assertEquals("reference link", plainText(ref.children))
    }

    @Test
    fun `SYN-03 nested ordered and unordered lists (corpus C-05)`() {
        val lists = Corpus.load("lists-nested.md").blocks.filterIsInstance<ListBlock>()
        val bullets = lists[0]
        assertFalse(bullets.ordered)
        assertEquals(2, bullets.items.size)
        val fruit = bullets.items[0].blocks
        assertInstanceOf(Paragraph::class.java, fruit[0])
        val inner = fruit[1] as ListBlock
        assertEquals(2, inner.items.size)
        val steps = lists[1]
        assertTrue(steps.ordered)
        assertEquals(1, steps.startNumber)
        assertEquals(5, lists[2].startNumber)
        assertTrue(bullets.tight)
        assertFalse(lists.last().tight)
    }

    @Test
    fun `SYN-03 task items carry a read-only state (corpus C-06)`() {
        val list =
            Corpus
                .load("tasks.md")
                .blocks
                .filterIsInstance<ListBlock>()
                .single()
        assertEquals(
            listOf(TaskState.CHECKED, TaskState.UNCHECKED, TaskState.CHECKED, TaskState.UNCHECKED, null),
            list.items.map { it.task },
        )
        val child =
            list.items[3]
                .blocks
                .filterIsInstance<ListBlock>()
                .single()
        assertEquals(listOf(TaskState.CHECKED, TaskState.UNCHECKED), child.items.map { it.task })
        val firstText = (list.items[0].blocks[0] as Paragraph).content
        assertEquals("Done item", plainText(firstText))
    }

    @Test
    fun `SYN-04 block quotes nest (corpus C-07)`() {
        val quote =
            Corpus
                .load("quotes-and-callouts.md")
                .blocks
                .filterIsInstance<BlockQuote>()
                .first()
        assertEquals(2, quote.blocks.size)
        assertInstanceOf(BlockQuote::class.java, quote.blocks[1])
    }

    @Test
    fun `SYN-05 fenced code keeps language and text, indented code has no language (corpus C-08)`() {
        val code = Corpus.load("code-languages.md").blocks.filterIsInstance<CodeBlock>()
        val langs = code.mapNotNull { it.language }
        for (l in listOf(
            "kotlin",
            "java",
            "python",
            "javascript",
            "typescript",
            "json",
            "yaml",
            "toml",
            "bash",
            "sql",
            "html",
            "css",
            "markdown",
            "c",
            "cpp",
            "go",
            "rust",
            "swift",
            "diff",
        )) {
            assertTrue(l in langs, "missing language $l")
        }
        val kotlin = code.first { it.language == "kotlin" }
        assertEquals("fun main() { println(\"hello\") }", kotlin.code)
        assertTrue(kotlin.fenced)
        val indented = code.first { !it.fenced }
        assertNull(indented.language)
        assertEquals("indented code block\nsecond line", indented.code)
        assertNull(code.last().language)
    }

    @Test
    fun `SYN-05 300 line code block stays one block (corpus C-09)`() {
        val code =
            Corpus
                .load("code-long-block.md")
                .blocks
                .filterIsInstance<CodeBlock>()
                .single()
        assertEquals(300, code.code.lines().size)
        assertTrue(code.code.startsWith("val line001 = compute(1)"))
        assertTrue(code.code.endsWith("// line 300"))
    }

    @Test
    fun `SYN-05 long lines are preserved verbatim (corpus C-10)`() {
        val code =
            Corpus
                .load("code-long-lines.md")
                .blocks
                .filterIsInstance<CodeBlock>()
                .single()
        assertEquals(5, code.code.lines().size)
        assertTrue(code.code.lines().all { it.length > 150 })
    }

    @Test
    fun `SYN-06 table alignment (corpus C-11)`() {
        val tables = Corpus.load("tables-simple.md").blocks.filterIsInstance<Table>()
        val t = tables[0]
        assertEquals(
            listOf(ColumnAlignment.LEFT, ColumnAlignment.CENTER, ColumnAlignment.RIGHT, ColumnAlignment.NONE),
            t.alignments,
        )
        assertEquals(listOf("Left", "Center", "Right", "Default"), t.header.cells.map { plainText(it.content) })
        assertEquals(3, t.rows.size)
        assertTrue(
            t.rows[2]
                .cells[0]
                .content
                .single() is Inline.Strong,
        )
        assertEquals(2, tables[1].columnCount)
    }

    @Test
    fun `SYN-06 tall and wide tables keep every row and column (corpus C-12 C-13)`() {
        val tall =
            Corpus
                .load("tables-tall.md")
                .blocks
                .filterIsInstance<Table>()
                .single()
        assertEquals(80, tall.rows.size)
        assertEquals(
            "row 80",
            plainText(
                tall.rows
                    .last()
                    .cells[0]
                    .content,
            ),
        )
        val wide =
            Corpus
                .load("tables-wide.md")
                .blocks
                .filterIsInstance<Table>()
                .single()
        assertEquals(14, wide.columnCount)
        assertEquals(5, wide.rows.size)
        assertTrue(wide.rows.all { it.cells.size == 14 })
    }

    @Test
    fun `SYN-15 horizontal rules become thematic breaks`() {
        val doc = parse("a\n\n---\n\nb\n\n***\n")
        assertEquals(2, doc.blocks.count { it is ThematicBreak })
    }

    @Test
    fun `DOC-07 every block range slices the source it came from`() {
        val doc = parse("# Title\n\nPara one.\n\n- a\n- b\n\n```kotlin\nx\n```\n")
        assertEquals("# Title", doc.source.substring(doc.blocks[0].range.start, doc.blocks[0].range.end))
        assertEquals("Para one.", doc.source.substring(doc.blocks[1].range.start, doc.blocks[1].range.end))
        assertEquals("- a\n- b", doc.source.substring(doc.blocks[2].range.start, doc.blocks[2].range.end))
        assertEquals("```kotlin\nx\n```", doc.source.substring(doc.blocks[3].range.start, doc.blocks[3].range.end))
    }

    @Test
    fun `DOC-07 ranges stay correct after CRLF normalization`() {
        val doc = MarkdownLoader.load("# T\r\n\r\nbody\r\n".toByteArray()) as LoadResult.Loaded
        val b = doc.document.blocks[1].range
        assertEquals("body", doc.document.source.substring(b.start, b.end))
    }

    @Test
    fun `DOC-08 nesting is capped at 32 and nothing is dropped (corpus C-23)`() {
        val doc = Corpus.load("deep-nesting.md")
        assertEquals(32, nestingDepth(doc.blocks.filterIsInstance<ListBlock>()))
        assertEquals(32, nestingDepth(doc.blocks.filterIsInstance<BlockQuote>()))
        val texts = allBlocks(doc.blocks).filterIsInstance<Paragraph>().map { plainText(it.content) }
        for (n in 1..40) assertTrue("level $n" in texts, "level $n lost")
        assertTrue("deep quote text" in texts)
    }

    @Test
    fun `DOC-08 flattened content keeps document order`() {
        val doc = Corpus.load("deep-nesting.md")
        val levels =
            allBlocks(doc.blocks.filterIsInstance<ListBlock>())
                .filterIsInstance<Paragraph>()
                .map { plainText(it.content) }
        assertEquals((1..40).map { "level $it" }, levels)
    }

    @Test
    fun `DOC-09 allowed inline HTML maps to styles and break (corpus C-20)`() {
        val doc = Corpus.load("html-inline.md")
        val p =
            doc.blocks
                .filterIsInstance<Paragraph>()
                .first()
                .content

        fun styled(s: InlineStyle) =
            p.filterIsInstance<Inline.Styled>().filter { it.style == s }.map { plainText(it.children) }
        assertEquals(listOf("2"), styled(InlineStyle.SUBSCRIPT))
        assertEquals(listOf("2"), styled(InlineStyle.SUPERSCRIPT))
        assertEquals(listOf("Ctrl", "C"), styled(InlineStyle.KEYBOARD))
        assertEquals(listOf("marked text"), styled(InlineStyle.MARK))
        assertEquals(listOf("underlined text"), styled(InlineStyle.UNDERLINE))
        assertTrue(p.contains(Inline.HardBreak))
        assertFalse(p.any { it is Inline.Text && it.text.contains("<sub>") })
    }

    @Test
    fun `DOC-09 other inline and block HTML renders as plain text`() {
        val doc = Corpus.load("html-inline.md")
        val paras = doc.blocks.filterIsInstance<Paragraph>().map { plainText(it.content) }
        assertTrue(
            paras.any {
                it.contains("<span style=\"color:red\">span</span>") &&
                    it.contains("<script>alert(1)</script>")
            },
        )
        assertTrue(
            paras.any {
                it.startsWith("<div class=\"box\">") &&
                    it.contains("A raw HTML block that must render as plain text.")
            },
        )
    }

    @Test
    fun `DOC-09 details and summary become a collapsible block`() {
        val d =
            Corpus
                .load("html-inline.md")
                .blocks
                .filterIsInstance<HtmlDetails>()
                .single()
        assertEquals("More information", plainText(d.summary))
        assertFalse(d.open)
        val body = d.blocks.single() as Paragraph
        assertTrue(body.content.any { it is Inline.Strong })
    }

    @Test
    fun `DOC-09 unclosed details keeps its content and open attribute`() {
        val doc = parse("<details open>\n<summary>S</summary>\n\nBody text\n")
        val d = doc.blocks.single() as HtmlDetails
        assertTrue(d.open)
        assertEquals("Body text", plainText((d.blocks.single() as Paragraph).content))
    }

    @Test
    fun `DOC-09 single block details with inline body`() {
        val doc = parse("<details><summary>Sum</summary>Inner text</details>\n")
        val d = doc.blocks.single() as HtmlDetails
        assertEquals("Sum", plainText(d.summary))
        assertEquals("Inner text", plainText((d.blocks.single() as Paragraph).content))
    }

    @Test
    fun `DOC-09 unmatched style tags stay as text`() {
        val p = parse("a <kbd>b and </u> c\n").blocks.single() as Paragraph
        assertEquals("a <kbd>b and </u> c", plainText(p.content))
    }

    @Test
    fun `corpus C-27 empty and whitespace-only documents have no blocks`() {
        for (name in listOf("empty.md", "whitespace-only.md")) {
            val doc = Corpus.load(name)
            assertTrue(doc.blocks.isEmpty(), name)
            assertNull(doc.title)
        }
    }

    @Test
    fun `DOC-06 parse is deterministic`() {
        val a = Corpus.load("quotes-and-callouts.md")
        val b = Corpus.load("quotes-and-callouts.md")
        assertEquals(a, b)
    }
}
