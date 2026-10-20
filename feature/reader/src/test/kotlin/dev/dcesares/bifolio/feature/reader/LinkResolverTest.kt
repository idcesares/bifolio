package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI-09, NAV-07, SYN-09: ids to definitions, duplicate ids, and what a link destination asks for. */
class LinkResolverTest {
    private val doc =
        MarkdownParser.parse(
            """
            # Intro

            Text with a note.[^a] And another.[^Dup] And a ghost.[^ghost]

            ## Café au lait

            [^a]: First note.

                Second paragraph.

            [^Dup]: One.

            [^Dup]: Two.

            > [^q]: Inside a quote.

            ## Last
            """.trimIndent() + "\n",
        )
    private val notes = NoteIndex(doc.blocks)
    private val links = LinkResolver(doc.blocks)

    private fun text(definition: NoteDefinition): String =
        plainText((definition.block.blocks.first() as Paragraph).content)

    @Test
    fun `a label resolves to its definition and the top level block that holds it`() {
        val definition = notes.definitionOf("a")!!
        assertEquals("First note.", text(definition))
        assertEquals(2, definition.block.blocks.size)
        assertEquals(doc.blocks.indexOf(definition.block), definition.blockIndex)
    }

    @Test
    fun `a label defined twice resolves to the first definition`() {
        assertEquals("One.", text(notes.definitionOf("Dup")!!))
    }

    @Test
    fun `an undefined label has no definition`() {
        assertNull(notes.definitionOf("ghost"))
        assertNull(notes.definitionOf(""))
    }

    @Test
    fun `lookup ignores case when the exact label is not defined`() {
        assertEquals("One.", text(notes.definitionOf("dup")!!))
        assertEquals("First note.", text(notes.definitionOf("A")!!))
    }

    @Test
    fun `a definition nested in a container resolves to the top level container`() {
        val definition = notes.definitionOf("q")
        if (definition != null) assertTrue(doc.blocks[definition.blockIndex] !== definition.block)
    }

    @Test
    fun `a heading fragment jumps to its block, decoded and case folded`() {
        val head = links.resolve("#caf%C3%A9-au-lait")
        assertTrue(head is LinkAction.Jump)
        assertEquals(links.resolve("#café-au-lait"), head)
        assertEquals(links.resolve("#Café-Au-Lait"), head)
        assertEquals(LinkAction.Jump(Anchor(0, 0)), links.resolve("#intro"))
    }

    @Test
    fun `a fragment naming a footnote jumps to the definition`() {
        val a = notes.definitionOf("a")!!
        assertEquals(LinkAction.Jump(Anchor(a.blockIndex, 0)), links.resolve("#a"))
        assertEquals(LinkAction.Jump(Anchor(a.blockIndex, 0)), links.resolve("#fn:a"))
        assertEquals(LinkAction.Jump(Anchor(a.blockIndex, 0)), links.resolve("#^a"))
    }

    @Test
    fun `a heading wins over a footnote with the same name`() {
        val clash = MarkdownParser.parse("# Last\n\ntext[^last]\n\n[^last]: note\n")
        assertEquals(LinkAction.Jump(Anchor(0, 0)), LinkResolver(clash.blocks).resolve("#last"))
    }

    @Test
    fun `unknown, empty and malformed fragments do nothing`() {
        assertEquals(LinkAction.None, links.resolve("#nope"))
        assertEquals(LinkAction.None, links.resolve("#"))
        assertEquals(LinkAction.None, links.resolve(""))
        assertEquals(LinkAction.None, links.resolve("#%E0%A4%A"))
    }

    @Test
    fun `web and mail destinations are external, everything else with a scheme is refused`() {
        assertEquals(LinkAction.External("https://example.org/a?b=1#c"), links.resolve("https://example.org/a?b=1#c"))
        assertEquals(LinkAction.External("http://example.org"), links.resolve("  http://example.org "))
        assertEquals(LinkAction.External("mailto:bifolio@dcesares.dev"), links.resolve("mailto:bifolio@dcesares.dev"))
        assertEquals(LinkAction.External("HTTPS://EXAMPLE.ORG"), links.resolve("HTTPS://EXAMPLE.ORG"))
        for (unsafe in listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://x/y",
            "intent://x#Intent;end",
            "data:text/html,x",
        )) {
            assertEquals(LinkAction.None, links.resolve(unsafe), unsafe)
        }
    }

    @Test
    fun `a relative path is not a link we follow`() {
        assertEquals(LinkAction.None, links.resolve("other.md"))
        assertEquals(LinkAction.None, links.resolve("other.md#intro"))
    }
}
