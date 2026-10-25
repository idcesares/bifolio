package dev.dcesares.bifolio.feature.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BACK_CONTROL = "Back to the library"
private const val WAIT_MILLIS = 20_000L

/**
 * A11Y-06: when the chrome hides while a control holds the focus, the focus goes back to the reading surface instead of
 * being lost. This runs the real [ReaderRoute] with a real [ReaderViewModel], not a stand-in for either.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ChromeHideFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var inputMode: InputModeManager

    private fun open(): ReaderViewModel {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xhdpi")
        val text = List(60) { "Paragraph $it. " + "The quick brown fox jumps over the lazy dog. ".repeat(6) }
        val viewModel =
            ReaderViewModel(
                reader = FakeReader(mapOf("doc" to raw(text.joinToString("\n\n")))),
                repository = RecentsRepository(InMemoryPreferences(), RecordingGrants()),
                preferences = ReadingPreferences(InMemoryPreferences()),
                engine = GridTextEngine(),
                computeDispatcher = Dispatchers.Main,
                chromeDispatcher = Dispatchers.Main,
            )
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme {
                    inputMode = LocalInputModeManager.current
                    ReaderRoute(viewModel = viewModel, onBack = {})
                }
            }
        }
        compose.runOnUiThread { viewModel.open(OpenRequest("doc", "text/markdown", persistGrant = false)) }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread { inputMode.requestInputMode(InputMode.Keyboard) }
        return viewModel
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun hidingTheChromeWithAControlFocusedGivesTheFocusBackToTheSurface() {
        val viewModel = open()
        press(Key.Tab)
        compose.waitUntil(WAIT_MILLIS) { viewModel.chromeVisible.value }
        compose.onNodeWithContentDescription(BACK_CONTROL).assertIsFocused()

        // A center tap hides the chrome while the Back control still holds the focus.
        compose.runOnUiThread { viewModel.toggleChrome() }
        compose.waitUntil(WAIT_MILLIS) { !viewModel.chromeVisible.value }
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(BACK_CONTROL).assertDoesNotExist()

        val before = viewModel.anchor.value
        assertEquals(Anchor(0, 0), before)
        press(Key.PageDown)
        compose.waitUntil(WAIT_MILLIS) { viewModel.anchor.value != before }
        assertNotEquals("page keys reach the surface after the chrome is gone", before, viewModel.anchor.value)
        assertTrue(viewModel.anchor.value.blockIndex >= 0)
    }
}
