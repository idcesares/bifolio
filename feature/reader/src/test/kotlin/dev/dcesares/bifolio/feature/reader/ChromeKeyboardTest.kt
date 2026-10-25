package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingPalettes
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BACK = "Back to the library"
private const val SMALLER = "Smaller text"

/** A11Y-06: the chrome is operable from the keyboard and shows where the focus is. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ChromeKeyboardTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0
    private var smaller = 0
    private lateinit var inputMode: InputModeManager

    private fun chrome(theme: ReadingThemeId = ReadingThemeId.Paper) {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xxhdpi")
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    // Stands in for the reading surface, which holds the focus while the chrome opens.
                    inputMode = LocalInputModeManager.current
                    val surface = remember { FocusRequester() }
                    LaunchedEffect(Unit) { surface.requestFocus() }
                    Box(Modifier.fillMaxSize().focusRequester(surface).focusable()) {
                        ReaderChrome(
                            visible = true,
                            actions =
                                ChromeActions(
                                    onBack = { backs++ },
                                    onAppearance = { smaller++ },
                                ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun accentPixels(
        description: String,
        theme: ReadingThemeId,
    ): Int {
        val accent = ReadingPalettes.of(theme).accent.toArgb()
        val bitmap = compose.onNodeWithContentDescription(description).captureToImage().asAndroidBitmap()
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) if (bitmap.getPixel(x, y) == accent) count++
        }
        return count
    }

    private fun tab() {
        // A keyboard user is in keyboard input mode, where clickables take focus.
        compose.runOnUiThread { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onRoot().performKeyInput { pressKey(Key.Tab) }
        compose.waitForIdle()
    }

    @Test
    fun tabMovesFocusThroughTheControlsInReadingOrder() {
        chrome()
        tab()
        compose.onNodeWithContentDescription(BACK).assertIsFocused()
        tab()
        compose.onNodeWithContentDescription(BACK).assertIsNotFocused()
        compose.onNodeWithContentDescription("Columns: Auto", substring = true).assertIsFocused()
    }

    @Test
    fun enterActivatesTheFocusedControl() {
        chrome()
        tab()
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(1, backs)
        repeat(3) { tab() }
        compose.onNodeWithContentDescription(SMALLER).assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(1, smaller)
    }

    private fun ringShows(theme: ReadingThemeId) {
        chrome(theme)
        val before = accentPixels(BACK, theme)
        tab()
        val after = accentPixels(BACK, theme)
        assertTrue("$theme: ring must add accent pixels ($before to $after)", after > before + RING_PIXELS)
        assertFalse("$theme: unfocused control stays plain", accentPixels(SMALLER, theme) > before + RING_PIXELS)
    }

    @Test
    fun focusedControlDrawsAnAccentRingOnPaper() = ringShows(ReadingThemeId.Paper)

    @Test
    fun focusedControlDrawsAnAccentRingOnSepia() = ringShows(ReadingThemeId.Sepia)

    @Test
    fun focusedControlDrawsAnAccentRingOnDusk() = ringShows(ReadingThemeId.Dusk)

    @Test
    fun focusedControlDrawsAnAccentRingOnNight() = ringShows(ReadingThemeId.Night)
}

private const val RING_PIXELS = 40
