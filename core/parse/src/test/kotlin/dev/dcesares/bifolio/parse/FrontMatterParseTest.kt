package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.Paragraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrontMatterParseTest {
    @Test
    fun `SYN-13 no front matter leaves the document untouched`() {
        val doc = MarkdownParser.parse("# Hi\n\ntext\n")
        assertTrue(doc.blocks.none { it is FrontMatter })
        assertNull(doc.language)
        assertEquals("Hi", doc.title)
    }

    @Test
    fun `SYN-13 title and lang are extracted and the block comes first`() {
        val src = "---\ntitle: My Book\nlang: pt-BR\nauthor: Ana\n---\n\n# Heading\n\nBody\n"
        val doc = MarkdownParser.parse(src)
        val fm = doc.blocks[0] as FrontMatter
        assertEquals("My Book", fm.title)
        assertEquals("pt-BR", fm.language)
        assertEquals("My Book", doc.title)
        assertEquals("pt-BR", doc.language)
        assertEquals(0, fm.range.start)
        assertEquals("---\ntitle: My Book\nlang: pt-BR\nauthor: Ana\n---", src.substring(0, fm.range.end))
        assertEquals(
            listOf("title" to "My Book", "lang" to "pt-BR", "author" to "Ana"),
            fm.entries,
        )
        assertTrue(doc.blocks[1] is Heading)
        val h = doc.blocks[1] as Heading
        assertEquals(src.indexOf("# Heading"), h.range.start)
    }

    @Test
    fun `SYN-13 front matter title wins over the first H1`() {
        val doc = MarkdownParser.parse("---\ntitle: Meta\n---\n# Other\n")
        assertEquals("Meta", doc.title)
    }

    @Test
    fun `SYN-13 quoted values and comments`() {
        val fm = fm("---\ntitle: \"A: b\"\nlang: 'en'\n# comment\nk: v # not a comment\n---\n")
        assertEquals("A: b", fm.title)
        assertEquals("en", fm.language)
        assertEquals("v # not a comment", fm.entries.first { it.first == "k" }.second)
    }

    @Test
    fun `SYN-13 unknown keys are kept in order and nested lines are ignored`() {
        val fm = fm("---\nz: 1\ntags:\n  - a\n  - b\na: 2\n---\n")
        assertEquals(listOf("z" to "1", "tags" to "", "a" to "2"), fm.entries)
        assertNull(fm.title)
    }

    @Test
    fun `SYN-13 empty block is a front matter with no entries`() {
        val doc = MarkdownParser.parse("---\n---\n\nText\n")
        val fm = doc.blocks[0] as FrontMatter
        assertTrue(fm.entries.isEmpty())
        assertNull(doc.language)
        assertTrue(doc.blocks[1] is Paragraph)
    }

    @Test
    fun `SYN-13 dots closer is accepted`() {
        val fm = fm("---\ntitle: T\n...\nText\n")
        assertEquals("T", fm.title)
    }

    @Test
    fun `SYN-13 unclosed fence stays normal content`() {
        val doc = MarkdownParser.parse("---\ntitle: T\n\nText\n")
        assertTrue(doc.blocks.none { it is FrontMatter })
        assertNull(doc.language)
    }

    @Test
    fun `SYN-13 fence not at file start is a thematic break`() {
        val doc = MarkdownParser.parse("Intro\n\n---\ntitle: T\n---\n")
        assertTrue(doc.blocks.none { it is FrontMatter })
        val doc2 = MarkdownParser.parse("\n---\ntitle: T\n---\n")
        assertTrue(doc2.blocks.none { it is FrontMatter })
    }

    @Test
    fun `SYN-13 BOM and CRLF are handled`() {
        val doc = MarkdownParser.parse("﻿---\r\ntitle: T\r\nlang: de\r\n---\r\n\r\nBody\r\n")
        val fm = doc.blocks[0] as FrontMatter
        assertEquals("T", fm.title)
        assertEquals("de", doc.language)
        assertEquals(0, fm.range.start)
        assertTrue(!doc.source.contains('\r') && !doc.source.startsWith("﻿"))
        val p = doc.blocks[1] as Paragraph
        assertEquals("Body", doc.source.substring(p.range.start, p.range.end).trim())
    }

    @Test
    fun `SYN-13 trailing spaces on fences are tolerated`() {
        val fm = fm("---  \ntitle: T\n---  \nx\n")
        assertEquals("T", fm.title)
    }

    private fun fm(src: String): FrontMatter = MarkdownParser.parse(src).blocks[0] as FrontMatter
}
