package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalloutParseTest {
    private fun blocks(md: String) = MarkdownParser.parse(md).blocks

    @Test
    fun `SYN-10 each marker becomes its callout kind and the marker line is removed`() {
        for (kind in CalloutKind.entries) {
            val callout = blocks("> [!${kind.name}]\n> Body text.\n").single() as Callout
            assertEquals(kind, callout.kind)
            val p = callout.blocks.single() as Paragraph
            assertEquals("Body text.", plainText(p.content))
        }
    }

    @Test
    fun `SYN-10 marker is case-insensitive`() {
        val callout = blocks("> [!wArNiNg]\n> careful\n").single() as Callout
        assertEquals(CalloutKind.WARNING, callout.kind)
    }

    @Test
    fun `SYN-10 marker alone gives an empty callout`() {
        val callout = blocks("> [!TIP]\n").single() as Callout
        assertEquals(CalloutKind.TIP, callout.kind)
        assertTrue(callout.blocks.isEmpty())
    }

    @Test
    fun `SYN-10 nested lists, code and further paragraphs stay as children`() {
        val md = "> [!IMPORTANT]\n> Intro\n>\n> - one\n> - two\n>\n> ```kotlin\n> val x = 1\n> ```\n"
        val callout = blocks(md).single() as Callout
        assertEquals(CalloutKind.IMPORTANT, callout.kind)
        assertEquals(3, callout.blocks.size)
        assertEquals("Intro", plainText((callout.blocks[0] as Paragraph).content))
        assertEquals(2, (callout.blocks[1] as ListBlock).items.size)
        assertEquals("val x = 1", (callout.blocks[2] as CodeBlock).code)
    }

    @Test
    fun `SYN-10 marker followed directly by a list`() {
        val callout = blocks("> [!NOTE]\n> - a\n> - b\n").single() as Callout
        assertTrue(callout.blocks.single() is ListBlock)
    }

    @Test
    fun `SYN-10 multi-line first paragraph keeps its lines after the marker`() {
        val callout = blocks("> [!NOTE]\n> line one\n> line two\n").single() as Callout
        val text = plainText((callout.blocks.single() as Paragraph).content)
        assertTrue(text.startsWith("line one") && text.endsWith("line two"), text)
    }

    @Test
    fun `SYN-10 plain blockquote stays a blockquote`() {
        assertTrue(blocks("> just a quote\n> more\n").single() is BlockQuote)
    }

    @Test
    fun `SYN-10 unknown marker stays a blockquote with the marker text`() {
        val quote = blocks("> [!FOO]\n> body\n").single() as BlockQuote
        assertTrue(plainText((quote.blocks.single() as Paragraph).content).startsWith("[!FOO]"))
    }

    @Test
    fun `SYN-10 marker not alone on its line stays a blockquote`() {
        assertTrue(blocks("> [!NOTE] inline text\n").single() is BlockQuote)
        assertTrue(blocks("> text [!NOTE]\n> more\n").single() is BlockQuote)
    }

    @Test
    fun `SYN-10 marker outside a quote is plain text`() {
        assertTrue(blocks("[!NOTE]\nbody\n").single() is Paragraph)
    }

    @Test
    fun `SYN-10 marker not in the first block of the quote stays a blockquote`() {
        assertTrue(blocks("> intro\n>\n> [!NOTE]\n> body\n").single() is BlockQuote)
    }

    @Test
    fun `SYN-10 callout range covers the quote and body ranges stay inside it`() {
        val md = "> [!NOTE]\n> Body here.\n"
        val callout = blocks(md).single() as Callout
        assertEquals(0, callout.range.start)
        val p = callout.blocks.single() as Paragraph
        assertTrue(md.substring(p.range.start, p.range.end).contains("Body here."))
    }

    @Test
    fun `SYN-10 callout nested in a list item and a quote nested in a callout`() {
        val list = blocks("- item\n\n  > [!CAUTION]\n  > hot\n").single() as ListBlock
        assertTrue(list.items[0].blocks.any { it is Callout })
        val callout = blocks("> [!NOTE]\n> text\n>\n> > inner quote\n").single() as Callout
        assertTrue(callout.blocks.last() is BlockQuote)
    }
}
