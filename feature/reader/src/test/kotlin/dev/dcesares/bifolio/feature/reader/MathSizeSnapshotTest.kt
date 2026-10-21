package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.MathSize
import dev.dcesares.bifolio.layout.MathSizeProvider
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

private val REQUEST =
    PageRequest(widthPx = 400, heightPx = 640, density = 2f, fontScale = 1f, bodySp = 18f, languageTag = "en")

/** SYN-12, PAG-07: formula sizes reach the paginator as one frozen snapshot with its own key. */
class MathSizeSnapshotTest {
    private val document =
        MarkdownParser.parse(
            "Intro.\n\n$$\na^2\n$$\n\n> quoted\n>\n> $$\n> b^2\n> $$\n\n- item\n\n  $$\n  c^2\n  $$\n",
        )

    @Test
    fun formulasInsideQuotesAndListsAreFound() {
        assertEquals(listOf("a^2", "b^2", "c^2"), mathBlocksOf(document.blocks).map { it.tex.trim() })
    }

    @Test
    fun anEmptySnapshotKnowsNothingAndHasKeyZero() {
        val (provider, key) = MathSizeSnapshot.EMPTY.forRequest(REQUEST)
        assertEquals(0, key)
        assertSame(MathSizeProvider.UNKNOWN, provider)
    }

    @Test
    fun newSizesMakeANewSnapshotWithANewKeyAndTheSameSizesChangeNothing() {
        val block = mathBlocksOf(document.blocks).first()
        val tag = MathTag(2f, 1f, 18f)
        val start = MathSizeSnapshot.EMPTY.restarted(tag)
        val known = mapOf(block.range.start to MathSize.Known(120, 40))
        val next = start.plus(known)
        assertNotEquals(start.key, next.key)
        assertEquals(MathSize.Known(120, 40), next.sizeOf(block))
        assertEquals(MathSize.Unknown, start.sizeOf(block))
        assertSame(next, next.plus(known))
    }

    @Test
    fun aSizeIsOnlyOfferedToARequestItWasMeasuredFor() {
        val block = mathBlocksOf(document.blocks).first()
        val snapshot =
            MathSizeSnapshot.EMPTY
                .restarted(MathTag(2f, 1f, 18f))
                .plus(mapOf(block.range.start to MathSize.Known(120, 40)))
        val (provider, key) = snapshot.forRequest(REQUEST)
        assertEquals(snapshot.key, key)
        assertSame(snapshot, provider)
        val other = REQUEST.copy(fontScale = 2f)
        assertEquals(0, snapshot.forRequest(other).second)
        assertEquals(0, snapshot.forRequest(REQUEST.copy(bodySp = 20f)).second)
    }
}
