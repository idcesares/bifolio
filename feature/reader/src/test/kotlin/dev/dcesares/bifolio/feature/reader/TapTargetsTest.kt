package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.TargetKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** A11Y-02, UI-09: the tap area of a marker is at least 48dp, and a link is tapped where it is drawn. */
class TapTargetsTest {
    private val side = 48f

    // A superscript marker 10 x 14 px with its center at (100, 50).
    private val marker = TargetBox(95f, 43f, 105f, 57f, TargetKind.NOTE, "1")
    private val link = TargetBox(0f, 40f, 90f, 60f, TargetKind.LINK, "#x")

    @Test
    fun `a marker is reached from 24 px around its center`() {
        assertSame(marker, pickTarget(listOf(marker), 100f + 23f, 50f - 23f, side))
        assertNull(pickTarget(listOf(marker), 100f + 25f, 50f, side))
        assertNull(pickTarget(listOf(marker), 100f, 50f + 25f, side))
    }

    @Test
    fun `a marker larger than the minimum keeps its own size`() {
        val big = TargetBox(0f, 0f, 100f, 100f, TargetKind.NOTE, "2")
        assertSame(big, pickTarget(listOf(big), 99f, 99f, side))
        assertNull(pickTarget(listOf(big), 101f, 50f, side))
    }

    @Test
    fun `a link is reached only inside its drawn rectangle`() {
        assertSame(link, pickTarget(listOf(link), 10f, 41f, side))
        assertNull(pickTarget(listOf(link), 10f, 30f, side))
    }

    @Test
    fun `a tap on the link text is not stolen by the enlarged area of a marker next to it`() {
        assertSame(link, pickTarget(listOf(marker, link), 85f, 50f, side))
        assertSame(marker, pickTarget(listOf(marker, link), 96f, 50f, side))
    }

    @Test
    fun `between two markers the nearer center wins`() {
        val other = TargetBox(115f, 43f, 125f, 57f, TargetKind.NOTE, "2")
        assertSame(marker, pickTarget(listOf(marker, other), 105f, 50f, side))
        assertSame(other, pickTarget(listOf(marker, other), 112f, 50f, side))
    }

    @Test
    fun `an empty list and a far tap pick nothing`() {
        assertNull(pickTarget(emptyList(), 1f, 1f, side))
        assertEquals(null, pickTarget(listOf(marker, link), 500f, 500f, side))
    }

    @Test
    fun `shifting moves the whole box`() {
        val moved = marker.shifted(10f, 20f)
        assertEquals(105f, moved.left)
        assertEquals(63f, moved.top)
        assertEquals(TargetKind.NOTE, moved.kind)
    }
}
