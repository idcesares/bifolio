package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** What a key asks of the reading surface (NAV-03, NAV-04, NAV-05). */
internal enum class ReaderCommand { NEXT, PREVIOUS, FIRST, LAST, SEARCH, TOC, CLOSE }

/** Fraction of the viewport a keyboard scroll moves in scroll mode (NAV-05). */
internal const val KEY_SCROLL_FRACTION = 0.9f

private val Plain: Map<Key, ReaderCommand> =
    mapOf(
        Key.DirectionRight to ReaderCommand.NEXT,
        Key.PageDown to ReaderCommand.NEXT,
        Key.J to ReaderCommand.NEXT,
        Key.DirectionLeft to ReaderCommand.PREVIOUS,
        Key.PageUp to ReaderCommand.PREVIOUS,
        Key.K to ReaderCommand.PREVIOUS,
        Key.MoveHome to ReaderCommand.FIRST,
        Key.MoveEnd to ReaderCommand.LAST,
        Key.T to ReaderCommand.TOC,
        Key.Escape to ReaderCommand.CLOSE,
    )

private val Volume: Map<Key, ReaderCommand> =
    mapOf(Key.VolumeDown to ReaderCommand.NEXT, Key.VolumeUp to ReaderCommand.PREVIOUS)

/**
 * Maps a key to a command, or null when the key is not ours. [ctrl] and [shift] are the modifier state.
 * Volume keys only map when [volumeTurns] is set (NAV-04): Volume Down goes forward, Volume Up goes back.
 */
internal fun readerCommandOf(
    key: Key,
    ctrl: Boolean,
    shift: Boolean,
    volumeTurns: Boolean,
): ReaderCommand? =
    when {
        ctrl -> ReaderCommand.SEARCH.takeIf { key == Key.F }
        key == Key.Spacebar -> if (shift) ReaderCommand.PREVIOUS else ReaderCommand.NEXT
        else -> Plain[key] ?: if (volumeTurns) Volume[key] else null
    }

/** The command for a key-down [event], or null for key-up, other keys, or keys that are not ours. */
internal fun KeyEvent.readerCommand(volumeTurns: Boolean): ReaderCommand? =
    if (type == KeyEventType.KeyDown) readerCommandOf(key, isCtrlPressed, isShiftPressed, volumeTurns) else null
