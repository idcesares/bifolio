package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SYN-08 block promotion and the DOC-09 boundary for `<img>`. */
class ImageBlockParseTest {
    private fun blocks(md: String) = allBlocks(MarkdownParser.parse(md).blocks)

    @Test
    fun `SYN-08 a paragraph that is one image becomes an atomic ImageBlock with its window`() {
        val md = "Before.\n\n![A *fine* view](img/a.png \"Title\")\n\nAfter.\n"
        val doc = MarkdownParser.parse(md)
        assertEquals(3, doc.blocks.size)
        val image = doc.blocks[1] as ImageBlock
        assertEquals("img/a.png", image.source)
        assertEquals("Title", image.title)
        assertEquals("A fine view", image.alt)
        assertEquals("![A *fine* view](img/a.png \"Title\")", md.substring(image.range.start, image.range.end))
    }

    @Test
    fun `SYN-08 images inside lists and quotes are promoted in place`() {
        val found = blocks("- ![a](a.png)\n\n> ![b](b.png)\n").filterIsInstance<ImageBlock>()
        assertEquals(listOf("a.png", "b.png"), found.map { it.source })
    }

    @Test
    fun `an image with text, several images or a link around it stay inline in a paragraph`() {
        val cases = listOf("see ![a](a.png) here\n", "![a](a.png) ![b](b.png)\n", "![a](a.png)\n![b](b.png)\n")
        for (md in cases + "[![a](a.png)](u)\n") {
            val b = MarkdownParser.parse(md).blocks.single()
            assertTrue(b is Paragraph, "paragraph expected for: $md")
        }
        val mixed = MarkdownParser.parse("see ![a](a.png) here\n").blocks.single() as Paragraph
        assertEquals("see a here", plainText(mixed.content))
        assertTrue(mixed.content.any { it is Inline.Image })
    }

    @Test
    fun `a hard break after the image does not stop the promotion`() {
        val b = MarkdownParser.parse("![a](a.png)  \n").blocks.single()
        assertTrue(b is ImageBlock, b.toString())
    }

    @Test
    fun `DOC-09 an img tag is never an image, whatever its source`() {
        for (md in listOf(
            "<img src=\"javascript:alert(1)\">\n",
            "<img src=\"https://example.com/a.png\" alt=\"x\">\n",
            "text <img src=\"a.png\"> text\n",
        )) {
            val found = blocks(md)
            assertTrue(found.none { it is ImageBlock }, "no ImageBlock for: $md")
            val all = found.filterIsInstance<Paragraph>().flatMap { it.content }
            assertTrue(all.none { it is Inline.Image }, "no Inline.Image for: $md")
            assertTrue(plainText(all).contains("<img"), "the tag stays visible text for: $md")
        }
    }
}
