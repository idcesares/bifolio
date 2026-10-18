package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** NAV-03, NAV-04, NAV-05: the key table. */
class ReaderKeysTest {
    private fun of(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        volume: Boolean = false,
    ) = readerCommandOf(key, ctrl, shift, volume)

    @Test
    fun forwardKeys() {
        for (key in listOf(Key.DirectionRight, Key.PageDown, Key.Spacebar, Key.J)) {
            assertEquals("$key", ReaderCommand.NEXT, of(key))
        }
    }

    @Test
    fun backKeys() {
        for (key in listOf(Key.DirectionLeft, Key.PageUp, Key.K)) {
            assertEquals("$key", ReaderCommand.PREVIOUS, of(key))
        }
        assertEquals(ReaderCommand.PREVIOUS, of(Key.Spacebar, shift = true))
    }

    @Test
    fun jumpsAndOverlays() {
        assertEquals(ReaderCommand.FIRST, of(Key.MoveHome))
        assertEquals(ReaderCommand.LAST, of(Key.MoveEnd))
        assertEquals(ReaderCommand.SEARCH, of(Key.F, ctrl = true))
        assertEquals(ReaderCommand.TOC, of(Key.T))
        assertEquals(ReaderCommand.CLOSE, of(Key.Escape))
    }

    @Test
    fun plainFAndCtrlOtherKeysAreNotOurs() {
        assertNull(of(Key.F))
        assertNull(of(Key.J, ctrl = true))
        assertNull(of(Key.A))
    }

    @Test
    fun volumeKeysTurnOnlyWhenEnabled() {
        assertNull(of(Key.VolumeDown))
        assertNull(of(Key.VolumeUp))
        assertEquals(ReaderCommand.NEXT, of(Key.VolumeDown, volume = true))
        assertEquals(ReaderCommand.PREVIOUS, of(Key.VolumeUp, volume = true))
    }
}
