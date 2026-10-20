package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI-08: highlights are found in the drawn text and paired with the engine's matches by source extent. */
class SearchHighlighterTest {
    private fun highlighter(
        md: String,
        query: String,
        focused: Int = 0,
        limit: Int = DocumentSearch.DEFAULT_LIMIT,
    ): Triple<Document, SearchResult, SearchHighlighter> {
        val doc = MarkdownParser.parse(md)
        val result = DocumentSearch.search(doc, query, limit)
        return Triple(doc, result, SearchHighlighter(doc, query, result, focused))
    }

    private fun flatten(block: Block): List<Block> =
        when (block) {
            is dev.dcesares.bifolio.model.ListBlock -> block.items.flatMap { it.blocks.flatMap(::flatten) }
            is dev.dcesares.bifolio.model.BlockQuote -> block.blocks.flatMap(::flatten)
            is dev.dcesares.bifolio.model.Callout -> block.blocks.flatMap(::flatten)
            is dev.dcesares.bifolio.model.FootnoteDefinition -> block.blocks.flatMap(::flatten)
            is HtmlDetails -> listOf(block) + block.blocks.flatMap(::flatten)
            else -> listOf(block)
        }

    /** Total highlights over every text unit the document draws, in reading order. */
    private fun all(
        doc: Document,
        h: SearchHighlighter,
    ): List<HighlightRange> = doc.blocks.flatMap(::flatten).flatMap { h.hitsIn(it) }

    @Test
    fun `an inactive highlighter finds nothing`() {
        val (doc, _, h) = highlighter("alpha beta\n", "  ")
        assertFalse(h.isActive)
        assertTrue(all(doc, h).isEmpty())
    }

    @Test
    fun `every match of the engine is highlighted exactly once and exactly one is focused`() {
        val md =
            "# Alpha\n\nAlpha beta alpha.\n\n- item ALPHA\n- another\n\n> quote with Álpha\n\n```\ncode alpha\n```\n"
        val (doc, result, h) = highlighter(md, "alpha", focused = 3)
        val hits = all(doc, h)
        assertEquals(result.matches.size, hits.size)
        assertEquals(listOf(3), hits.withIndex().filter { it.value.focused }.map { it.index })
    }

    @Test
    fun `highlight length is the rendered length of the match`() {
        val (doc, result, h) = highlighter("Um *São* João e `código`.\n", "são")
        val hit = h.hitsIn(doc.blocks[0]).single()
        assertEquals(result.matches[0].length, hit.end - hit.start)
        assertEquals("São", plain(doc.blocks[0]).substring(hit.start, hit.end))
    }

    private fun plain(block: Block): String =
        dev.dcesares.bifolio.model
            .plainText((block as Paragraph).content)

    @Test
    fun `a footnote reference before the match does not shift or lose it`() {
        val md = "Before[^1] the needle here.\n\n[^1]: Note with needle.\n"
        val (doc, result, h) = highlighter(md, "needle", focused = 1)
        assertEquals(2, result.matches.size)
        val hits = all(doc, h)
        assertEquals(2, hits.size)
        assertEquals(listOf(false, true), hits.map { it.focused })
        val first = h.hitsIn(doc.blocks[0]).single()
        assertEquals("needle", plain(doc.blocks[0]).substring(first.start, first.end))
    }

    @Test
    fun `a closed details block highlights its summary only and pairs the summary`() {
        val md = "<details>\n<summary>The needle summary</summary>\n\nHidden needle body.\n\n</details>\n"
        val (doc, result, h) = highlighter(md, "needle", focused = 0)
        assertEquals(2, result.matches.size)
        val details = doc.blocks[0] as HtmlDetails
        val summary = h.hitsIn(details)
        assertEquals(1, summary.size)
        assertTrue(summary.single().focused)
    }

    @Test
    fun `a multi word query across a soft line break is one highlight`() {
        val (doc, result, h) = highlighter("first\nsecond word here\n", "first second")
        assertEquals(1, result.matches.size)
        val hit = h.hitsIn(doc.blocks[0]).single()
        assertEquals(result.matches[0].length, hit.end - hit.start)
        assertTrue(hit.focused)
    }

    @Test
    fun `text units use their source extent so the same word elsewhere is not paired by mistake`() {
        val md = "one alpha\n\ntwo alpha alpha\n"
        val (doc, _, h) = highlighter(md, "alpha", focused = 2)
        assertEquals(listOf(false), h.hitsIn(doc.blocks[0]).map { it.focused })
        assertEquals(listOf(false, true), h.hitsIn(doc.blocks[1]).map { it.focused })
    }

    @Test
    fun `leaf text with a source extent pairs like the block does`() {
        val md = "one alpha\n\ntwo alpha alpha\n"
        val (doc, _, h) = highlighter(md, "alpha", focused = 1)
        val block = doc.blocks[1] as Paragraph
        val rendered = RenderedText.of(block.content, doc.source, block.range.start, block.range.end)
        val viaLeaf = h.hitsIn(rendered.text, rendered.map[0], rendered.map[rendered.text.length])
        assertEquals(h.hitsIn(block), viaLeaf)
        assertEquals(listOf(true, false), viaLeaf.map { it.focused })
    }

    @Test
    fun `text without a source extent is highlighted but never focused`() {
        val (_, _, h) = highlighter("alpha\n", "alpha")
        val hits = h.hitsIn("a cell with ALPHA inside", -1, -1)
        assertEquals(1, hits.size)
        assertFalse(hits.single().focused)
        assertEquals(12, hits.single().start)
        assertEquals(17, hits.single().end)
    }

    @Test
    fun `when truncation leaves a unit with fewer matches than hits nothing in it is focused`() {
        val md = "alpha alpha alpha alpha\n"
        val (doc, result, h) = highlighter(md, "alpha", focused = 0, limit = 2)
        assertTrue(result.truncated)
        val hits = h.hitsIn(doc.blocks[0])
        assertEquals(4, hits.size)
        assertTrue(hits.none { it.focused })
    }

    @Test
    fun `focus outside the result list focuses nothing`() {
        val (doc, _, h) = highlighter("alpha alpha\n", "alpha", focused = -1)
        assertTrue(h.hitsIn(doc.blocks[0]).none { it.focused })
        val (doc2, _, h2) = highlighter("alpha alpha\n", "alpha", focused = 9)
        assertTrue(h2.hitsIn(doc2.blocks[0]).none { it.focused })
    }

    @Test
    fun `code blocks are searched in their code`() {
        val (doc, result, h) = highlighter("```kotlin\nval Alpha = 1\n```\n", "alpha")
        assertEquals(1, result.matches.size)
        val code = doc.blocks[0] as CodeBlock
        val hit = h.hitsIn(code).single()
        assertEquals("Alpha", code.code.substring(hit.start, hit.end))
        assertTrue(hit.focused)
    }

    @Test
    fun `blocks that are not text units have no highlights`() {
        val (doc, _, h) = highlighter("alpha\n\n---\n", "alpha")
        assertTrue(h.hitsIn(doc.blocks[1]).isEmpty())
    }

    @Test
    fun `block level highlights agree with the engine on the seeded corpus`() {
        val dir = java.io.File("../../testdata/corpus")
        val files = dir.listFiles { f -> f.name.endsWith(".md") }.orEmpty().sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val md = file.readText().replace("\r\n", "\n")
            for (query in listOf("the", "e", "a b", "0")) {
                val (doc, result, h) = highlighter(md, query, focused = 0, limit = Int.MAX_VALUE)
                val paired = all(doc, h).count { it.focused }
                // Table cells, math and hidden children are matches without a drawn, mapped unit.
                // There is never more than one focus.
                assertTrue(paired <= 1, "${file.name} '$query'")
                assertTrue(all(doc, h).size <= result.matches.size, "${file.name} '$query'")
            }
        }
    }
}
