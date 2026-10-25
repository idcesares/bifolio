package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BACK_CONTROL = "Back to the library"

/**
 * A11Y-06, NAV-03: Tab on the reading surface shows the chrome and focuses its first control; Escape inside the chrome
 * gives the focus back to the surface, where the page keys work again. Real key presses, both surfaces.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ChromeFocusFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val document =
        MarkdownParser.parse(
            List(60) { "Paragraph $it. " + "The quick brown fox jumps over the lazy dog. ".repeat(6) }
                .joinToString("\n\n"),
        )
    private val settled = mutableListOf<Anchor>()
    private var tabs = 0
    private lateinit var inputMode: InputModeManager

    @Composable
    private fun Harness(surface: @Composable (onFocusChrome: () -> Unit, tick: Int) -> Unit) {
        inputMode = LocalInputModeManager.current
        var shown by remember { mutableStateOf(false) }
        var focusFirst by remember { mutableStateOf(false) }
        var tick by remember { mutableIntStateOf(0) }
        Box {
            surface({
                tabs++
                shown = true
                focusFirst = true
            }, tick)
            ReaderChrome(
                visible = shown,
                actions = ChromeActions(onReturnFocus = { tick++ }),
                focusFirst = focusFirst,
                onFocusedFirst = { focusFirst = false },
            )
        }
    }

    private fun show(surface: @Composable (() -> Unit, Int) -> Unit) {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xhdpi")
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme { Harness { onFocusChrome, tick -> surface(onFocusChrome, tick) } }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { inputMode.requestInputMode(InputMode.Keyboard) }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun scrollSurface() =
        show { onFocusChrome, tick ->
            ReaderScreen(
                ReaderUiState.Ready(document, "T", "h", emptySet(), Anchor(0, 0)),
                { settled += it },
                onFocusChrome = onFocusChrome,
                focusTick = tick,
            )
        }

    private fun pagedSurface() =
        show { onFocusChrome, tick ->
            PagedSurface(
                layout = buildLayout(document, PageRequest(824, 1830, 2f, 1f, 18f, "en")),
                initialAnchor = Anchor(0, 0),
                onRequest = {},
                onSettled = { settled += it },
                onFirstDrawn = {},
                onToggleChrome = {},
                onFocusChrome = onFocusChrome,
                focusTick = tick,
            )
        }

    private fun tabThenEscape() {
        compose.onNodeWithContentDescription(BACK_CONTROL).assertDoesNotExist()
        press(Key.Tab)
        assertEquals(1, tabs)
        compose.onNodeWithContentDescription(BACK_CONTROL).assertIsFocused()
        press(Key.Escape)
        compose.onNodeWithContentDescription(BACK_CONTROL).assertIsNotFocused()
        settled.clear()
        press(Key.PageDown)
        assertTrue("page keys reach the surface again", settled.isNotEmpty())
    }

    @Test
    fun scrollSurfaceTabShowsAndFocusesTheChromeAndEscapeReturns() {
        scrollSurface()
        tabThenEscape()
    }

    @Test
    fun pagedSurfaceTabShowsAndFocusesTheChromeAndEscapeReturns() {
        pagedSurface()
        tabThenEscape()
    }

    @Test
    fun shiftTabOnTheSurfaceAlsoOpensTheChrome() {
        pagedSurface()
        compose.onRoot().performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Tab)
            keyUp(Key.ShiftLeft)
        }
        compose.waitForIdle()
        assertEquals(1, tabs)
        compose.onNodeWithContentDescription(BACK_CONTROL).assertIsFocused()
    }

    @Test
    fun tabMovesOnToTheNextControlWhenTheSurfaceHasNoChromeToOpen() {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xhdpi")
        compose.setContent {
            BifolioTheme {
                inputMode = LocalInputModeManager.current
                Column {
                    Box(Modifier.weight(1f)) {
                        ReaderScreen(
                            ReaderUiState.Ready(document, "T", "h", emptySet(), Anchor(0, 0)),
                            { settled += it },
                        )
                    }
                    Box(Modifier.size(48.dp).testTag("next").focusable())
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { inputMode.requestInputMode(InputMode.Keyboard) }
        // The first Tab enters the surface's own focus target, the second leaves it; none is taken for the chrome.
        repeat(2) { press(Key.Tab) }
        // Not consumed by the surface, so the focus moves on like for any other control.
        compose.onNodeWithTag("next").assertIsFocused()
    }
}
