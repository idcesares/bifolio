package dev.dcesares.bifolio.model

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnchorTest {
    private val source = "# Title\n\nFirst para.\n\n\nSecond para.\n"

    private fun doc(): Document {
        val blocks =
            listOf(
                Heading(1, listOf(Inline.Text("Title")), "title", SourceRange(0, 7)),
                Paragraph(listOf(Inline.Text("First para.")), SourceRange(9, 20)),
                Paragraph(listOf(Inline.Text("Second para.")), SourceRange(22, 34)),
            )
        return Document(title = "Title", source = source, blocks = blocks)
    }

    @Test
    fun `ANC-01 anchor rejects negative components`() {
        assertThrows(IllegalArgumentException::class.java) { Anchor(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { Anchor(0, -1) }
    }

    @Test
    fun `ADR-0005 anchor is block index plus offset inside the block`() {
        val d = doc()
        assertEquals(Anchor(1, 4), d.anchorAt(13))
        assertEquals(13, d.sourceOffsetOf(Anchor(1, 4)))
    }

    @Test
    fun `ADR-0005 offset in a gap between blocks snaps to the next block start`() {
        val d = doc()
        assertEquals(Anchor(2, 0), d.anchorAt(21))
        assertEquals(Anchor(1, 0), d.anchorAt(8))
    }

    @Test
    fun `ANC-05 offsets are clamped to the source`() {
        val d = doc()
        assertEquals(Anchor(2, 12), d.anchorAt(10_000))
        assertEquals(Anchor(0, 0), d.anchorAt(-5))
        assertEquals(34, d.sourceOffsetOf(Anchor(2, 9_999)))
        assertEquals(34, d.sourceOffsetOf(Anchor(99, 0)))
    }

    @Test
    fun `ANC-05 empty document anchors at zero`() {
        val d = Document(title = null, source = "", blocks = emptyList())
        assertEquals(Anchor(0, 0), d.anchorAt(7))
        assertEquals(0, d.sourceOffsetOf(Anchor(3, 3)))
    }

    @Test
    fun `ADR-0005 anchor to offset to anchor is stable for every sampled offset`() {
        val d = doc()
        val offsets = Arb.int(0 until source.length).generate(RandomSource.seeded(42)).take(500)
        for (sample in offsets) {
            val a = d.anchorAt(sample.value)
            assertEquals(a, d.anchorAt(d.sourceOffsetOf(a)), "offset ${sample.value}")
            val block = d.blocks[a.blockIndex]
            val resolved = d.sourceOffsetOf(a)
            assertTrue(resolved >= block.range.start && resolved <= block.range.end)
        }
    }
}
