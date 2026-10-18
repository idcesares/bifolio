package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w412dp-h915dp-xhdpi"

/** NAV-03, NAV-04, NAV-05: keys turn pages, scroll by a viewport and jump, in both surfaces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReaderKeyNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private val document =
        MarkdownParser.parse(
            List(60) { "Paragraph $it. " + "The quick brown fox jumps over the lazy dog. ".repeat(6) }
                .joinToString("\n\n"),
        )

    private val settled = mutableListOf<Anchor>()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun paged(volume: Boolean = false) {
        RuntimeEnvironment.setQualifiers("+$PHONE")
        val layout = buildLayout(document, PageRequest(824, 1830, 2f, 1f, 18f, "en"))
        compose.mainClock.autoAdvance = true
        compose.setContent {
            BifolioTheme {
                PagedSurface(
                    layout = layout,
                    initialAnchor = Anchor(0, 0),
                    onRequest = {},
                    onSettled = { settled += it },
                    onFirstDrawn = {},
                    onToggleChrome = {},
                    volumeKeysTurn = volume,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun scroll(volume: Boolean = false) {
        RuntimeEnvironment.setQualifiers("+$PHONE")
        val ready = ReaderUiState.Ready(document, "T", "h", emptySet(), Anchor(0, 0))
        compose.setContent {
            BifolioTheme { ReaderScreen(ready, { settled += it }, volumeKeysTurn = volume) }
        }
        compose.waitForIdle()
    }

    @Test
    fun pagedForwardAndBackKeysTurnSpreads() {
        paged()
        press(Key.PageDown)
        assertEquals(1, settled.size)
        val second = settled.last()
        assertTrue("moved forward: $second", second.blockIndex > 0 || second.charOffset > 0)
        press(Key.K)
        assertEquals(Anchor(0, 0), settled.last())
    }

    @Test
    fun pagedEndAndHomeJump() {
        paged()
        press(Key.MoveEnd)
        val last = settled.last()
        assertTrue("End reaches the tail: $last", last.blockIndex > 30)
        press(Key.MoveHome)
        assertEquals(Anchor(0, 0), settled.last())
    }

    @Test
    fun pagedVolumeKeysOnlyWhenEnabled() {
        paged(volume = false)
        press(Key.VolumeDown)
        assertTrue(settled.isEmpty())
    }

    @Test
    fun pagedVolumeDownTurnsWhenEnabled() {
        paged(volume = true)
        press(Key.VolumeDown)
        assertEquals(1, settled.size)
    }

    @Test
    fun scrollPageDownMovesByAViewportAndPageUpGoesBack() {
        scroll()
        press(Key.PageDown)
        val after = settled.last()
        assertTrue("PageDown scrolls: $after", after.blockIndex > 0 || after.charOffset > 0)
        press(Key.PageUp)
        assertEquals(0, settled.last().blockIndex)
    }

    @Test
    fun scrollEndAndHomeJump() {
        scroll()
        press(Key.MoveEnd)
        assertTrue("End reaches the tail: ${settled.lastOrNull()}", (settled.lastOrNull()?.blockIndex ?: 0) > 30)
        press(Key.MoveHome)
        assertEquals(0, settled.last().blockIndex)
    }

    @Test
    fun scrollJumpRequestShowsTheAnchorAndReportsIt() {
        val jumps = MutableSharedFlow<Anchor>(extraBufferCapacity = 1)
        RuntimeEnvironment.setQualifiers("+$PHONE")
        val ready = ReaderUiState.Ready(document, "T", "h", emptySet(), Anchor(0, 0))
        compose.setContent { BifolioTheme { ReaderScreen(ready, { settled += it }, jumps = jumps) } }
        compose.waitForIdle()
        press(Key.MoveEnd)
        jumps.tryEmit(Anchor(3, 0))
        compose.waitForIdle()
        // The list reports its first visible item, which can be the block just above the target under the top padding.
        assertTrue("shown near block 3: ${settled.last()}", settled.last().blockIndex in 2..3)
    }
}
