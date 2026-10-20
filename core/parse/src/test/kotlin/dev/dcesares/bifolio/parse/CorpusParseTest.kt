package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.MermaidFence
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Corpus files C-18, C-19, C-21 and the callouts of quotes-and-callouts.md. */
class CorpusParseTest {
    private fun refLabels(inlines: List<Inline>): List<String> =
        inlines.flatMap { i ->
            when (i) {
                is Inline.FootnoteReference -> listOf(i.label)
                is Inline.Emphasis -> refLabels(i.children)
                is Inline.Strong -> refLabels(i.children)
                is Inline.Link -> refLabels(i.children)
                else -> emptyList()
            }
        }

    @Test
    fun `corpus lists the new files`() {
        val names = Corpus.names()
        assertTrue(names.containsAll(listOf("footnotes.md", "front-matter.md", "mermaid.md")), names.toString())
    }

    @Test
    fun `SYN-09 C-18 footnotes cover refs, multi-paragraph, undefined and duplicate ids`() {
        val blocks = Corpus.load("footnotes.md").blocks
        val defs = blocks.filterIsInstance<FootnoteDefinition>()
        assertEquals(listOf("patience", "short", "twice", "twice", "unused"), defs.map { it.label })
        assertEquals(3, defs[0].blocks.size)
        assertTrue(defs[0].blocks[2] is CodeBlock)
        val refs = blocks.filterIsInstance<Paragraph>().flatMap { refLabels(it.content) }
        assertEquals(listOf("patience", "short", "short", "twice"), refs)
        val undefined = blocks.filterIsInstance<Paragraph>().first { "undefined" in plainText(it.content) }
        assertTrue(plainText(undefined.content).contains("[^undefined]"))
    }

    @Test
    fun `SYN-13 C-19 front matter gives title and lang and a heading follows`() {
        val doc = Corpus.load("front-matter.md")
        val fm = doc.blocks[0] as FrontMatter
        assertEquals("Notas de leitura: um teste", fm.title)
        assertEquals("pt-BR", fm.language)
        assertEquals("Notas de leitura: um teste", doc.title)
        assertEquals("pt-BR", doc.language)
        assertEquals("some unknown value", fm.entries.first { it.first == "x-custom-key" }.second)
        assertTrue(doc.blocks[1] is Heading)
    }

    @Test
    fun `SYN-14 C-21 mermaid fence is a MermaidFence and other code stays code`() {
        val blocks = Corpus.load("mermaid.md").blocks
        val fence = blocks.filterIsInstance<MermaidFence>().single()
        assertTrue(fence.source.startsWith("graph TD"), fence.source)
        val code = blocks.filterIsInstance<CodeBlock>().single()
        assertEquals("kotlin", code.language)
    }

    @Test
    fun `SYN-10 quotes-and-callouts corpus parses every callout kind`() {
        val callouts = allBlocks(Corpus.load("quotes-and-callouts.md").blocks).filterIsInstance<Callout>()
        val kinds = callouts.map { it.kind }
        assertTrue(kinds.toSet().containsAll(CalloutKind.entries.toSet()), kinds.toString())
        assertTrue(callouts.any { it.blocks.size >= 2 })
    }
}
