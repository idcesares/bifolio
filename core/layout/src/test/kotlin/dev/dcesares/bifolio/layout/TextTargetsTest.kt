package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI-09, NAV-07: links and footnote markers are tappable runs of the drawn text, found from the inline tree. */
class TextTargetsTest {
    private fun rendered(
        md: String,
        markers: Boolean,
    ): Rendered {
        val doc = MarkdownParser.parse(md)
        val block = doc.blocks.first() as Paragraph
        return RenderedText.of(block.content, doc.source, block.range.start, block.range.end, markers)
    }

    @Test
    fun `a marker draws its label as superscript note text when markers are on`() {
        val r = rendered("Word[^n] and more.\n\n[^n]: Note.\n", markers = true)
        assertEquals("Wordn and more.", r.text)
        val note = r.targets.single()
        assertEquals(TargetKind.NOTE, note.kind)
        assertEquals("n", note.value)
        assertEquals(4, note.start)
        assertEquals(5, note.end)
        val i =
            r.styles.starts.indices
                .single { r.styles.starts[it] == 4 }
        assertTrue(r.styles.flags[i] and StyleFlag.NOTE != 0)
        assertTrue(r.styles.flags[i] and StyleFlag.SUPERSCRIPT != 0)
    }

    @Test
    fun `search text stays free of marker labels`() {
        val r = rendered("Word[^n] and more.\n\n[^n]: Note.\n", markers = false)
        assertEquals("Word and more.", r.text)
        assertTrue(r.targets.isEmpty())
    }

    @Test
    fun `marker characters keep the source map non-decreasing and later text aligned`() {
        val md = "Word[^note] and more.\n\n[^note]: Note.\n"
        val r = rendered(md, markers = true)
        assertEquals("Wordnote and more.", r.text)
        for (i in 1 until r.map.size) assertTrue(r.map[i] >= r.map[i - 1], "map must not decrease at $i")
        assertEquals(md.indexOf(" and"), r.map[r.text.indexOf(" and")])
    }

    @Test
    fun `a link is a target over its visible text with its destination as written`() {
        val r = rendered("See [the part](#part-one) now.\n", markers = false)
        assertEquals("See the part now.", r.text)
        val link = r.targets.single()
        assertEquals(TargetKind.LINK, link.kind)
        assertEquals("#part-one", link.value)
        assertEquals("the part", r.text.substring(link.start, link.end))
    }

    @Test
    fun `an external link and a nested emphasis link keep their full destination`() {
        val r = rendered("A [*bold* site](https://example.org/a?b=1#c) here.\n", markers = false)
        val link = r.targets.single()
        assertEquals("https://example.org/a?b=1#c", link.value)
        assertEquals("bold site", r.text.substring(link.start, link.end))
    }

    @Test
    fun `a link and a marker in one paragraph are two targets in text order`() {
        val r = rendered("[text](#x) tail[^n].\n\n[^n]: Note.\n", markers = true)
        assertEquals(listOf(TargetKind.LINK, TargetKind.NOTE), r.targets.map { it.kind })
        assertEquals("n", r.text.substring(r.targets[1].start, r.targets[1].end))
    }
}
