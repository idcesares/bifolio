package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FootnoteParseTest {
    private fun blocks(md: String) = MarkdownParser.parse(md).blocks

    private fun refs(inlines: List<Inline>): List<Inline.FootnoteReference> =
        inlines.flatMap { i ->
            when (i) {
                is Inline.FootnoteReference -> listOf(i)
                is Inline.Emphasis -> refs(i.children)
                is Inline.Strong -> refs(i.children)
                is Inline.Link -> refs(i.children)
                else -> emptyList()
            }
        }

    @Test
    fun `SYN-09 reference becomes a footnote reference inline and definition a block`() {
        val b = blocks("Text[^a] more.\n\n[^a]: The note.\n")
        assertEquals(2, b.size)
        val p = b[0] as Paragraph
        assertEquals(listOf("a"), refs(p.content).map { it.label })
        assertEquals("Text more.", plainText(p.content))
        val def = b[1] as FootnoteDefinition
        assertEquals("a", def.label)
        assertEquals("The note.", plainText((def.blocks.single() as Paragraph).content))
    }

    @Test
    fun `SYN-09 reference inside emphasis and list items is converted`() {
        val b = blocks("*em[^n]*\n\n- item[^n]\n\n[^n]: note\n")
        val em = (b[0] as Paragraph).content.filterIsInstance<Inline.Emphasis>().single()
        assertEquals(listOf("n"), refs(em.children).map { it.label })
        assertTrue(b.last() is FootnoteDefinition)
    }

    @Test
    fun `SYN-09 undefined reference stays literal text`() {
        val p = blocks("See[^missing] here.\n").single() as Paragraph
        assertTrue(refs(p.content).isEmpty())
        assertEquals("See[^missing] here.", plainText(p.content))
    }

    @Test
    fun `SYN-09 multi-paragraph definition keeps all its blocks`() {
        val md = "A[^1]\n\n[^1]: First.\n\n    Second paragraph.\n\n    ```\n    code\n    ```\n"
        val def = blocks(md).filterIsInstance<FootnoteDefinition>().single()
        assertEquals(3, def.blocks.size)
        assertEquals("First.", plainText((def.blocks[0] as Paragraph).content))
        assertEquals("Second paragraph.", plainText((def.blocks[1] as Paragraph).content))
        assertEquals("code", (def.blocks[2] as CodeBlock).code)
    }

    @Test
    fun `SYN-09 unused definition is still kept`() {
        val b = blocks("Plain.\n\n[^x]: Unused note.\n")
        val def = b[1] as FootnoteDefinition
        assertEquals("x", def.label)
    }

    @Test
    fun `SYN-09 duplicate definitions are all kept in source order and references share the label`() {
        val b = blocks("A[^d] B[^d]\n\n[^d]: one\n\n[^d]: two\n")
        val defs = b.filterIsInstance<FootnoteDefinition>()
        assertEquals(listOf("d", "d"), defs.map { it.label })
        assertEquals("one", plainText((defs[0].blocks.single() as Paragraph).content))
        assertEquals(listOf("d", "d"), refs((b[0] as Paragraph).content).map { it.label })
    }

    @Test
    fun `SYN-09 definition range covers its source and stays ordered`() {
        val md = "A[^1]\n\n[^1]: Note body.\n"
        val doc = MarkdownParser.parse(md)
        val def = doc.blocks[1] as Block
        assertTrue(md.substring(def.range.start, def.range.end).contains("Note body."))
        assertTrue(def.range.start >= doc.blocks[0].range.end)
    }
}
