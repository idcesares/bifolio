package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** UI-09, LAY-12: where the popover goes next to its marker. */
class FootnotePlacementTest {
    private val anchor = Rect(500f, 300f, 540f, 340f)

    private fun place(
        anchor: Rect = this.anchor,
        regionLeft: Int = 0,
        regionRight: Int = 1000,
        height: Int = 200,
    ) = placeBeside(anchor, 400, height, 900, regionLeft, regionRight, 20, 6)

    @Test
    fun itSitsBelowTheMarkerCenteredOnIt() {
        assertEquals(IntOffset(320, 346), place())
    }

    @Test
    fun itGoesAboveWhenThereIsNoRoomBelow() {
        val low = Rect(500f, 700f, 540f, 740f)
        assertEquals(IntOffset(320, 700 - 6 - 200), place(low))
    }

    @Test
    fun itStaysInsideTheRegionHorizontally() {
        assertEquals(20, place(Rect(0f, 300f, 20f, 340f)).x)
        assertEquals(1000 - 20 - 400, place(Rect(980f, 300f, 1000f, 340f)).x)
    }

    @Test
    fun itStaysInsideTheHalfOfAFold() {
        val right = place(Rect(700f, 300f, 740f, 340f), regionLeft = 560, regionRight = 1000)
        assertEquals(580, right.x)
        val left = place(Rect(300f, 300f, 340f, 340f), regionLeft = 0, regionRight = 480)
        assertEquals(480 - 20 - 400, left.x)
    }

    @Test
    fun withNoRoomEitherWayItSitsAtTheBottomMargin() {
        val middle = Rect(500f, 400f, 540f, 440f)
        assertEquals(900 - 20 - 600, place(middle, height = 600).y)
    }
}
