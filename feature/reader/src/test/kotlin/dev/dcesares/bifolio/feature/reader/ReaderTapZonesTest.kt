package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w412dp-h915dp-xhdpi"

/** NAV-02: outer 30% turn, center 40% toggles the chrome, in both surfaces. A tap never switches the reading mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReaderTapZonesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun tapAt(fraction: Float) {
        compose.onRoot().performTouchInput { click(Offset(width * fraction, height / 2f)) }
        compose.waitForIdle()
    }

    private fun scroll(toggles: () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$PHONE")
        val ready =
            ReaderUiState.Ready(MarkdownParser.parse("# T\n\nBody."), "T", "h", emptySet(), Anchor(0, 0))
        compose.setContent { BifolioTheme { ReaderScreen(ready, {}, onToggleChrome = toggles) } }
    }

    private fun paged(toggles: () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$PHONE")
        compose.setContent {
            BifolioTheme {
                PagedSurface(
                    layout = null,
                    initialAnchor = Anchor(0, 0),
                    onRequest = {},
                    onSettled = {},
                    onFirstDrawn = {},
                    onToggleChrome = toggles,
                )
            }
        }
    }

    @Test
    fun scrollCenterTapTogglesChromeAndOuterTapsDoNot() {
        var count = 0
        scroll { count++ }
        tapAt(0.5f)
        assertEquals(1, count)
        tapAt(0.31f)
        tapAt(0.69f)
        assertEquals(3, count)
        tapAt(0.1f)
        tapAt(0.29f)
        tapAt(0.71f)
        tapAt(0.9f)
        assertEquals(3, count)
    }

    @Test
    fun pagedCenterTapTogglesChromeAndOuterTapsDoNot() {
        var count = 0
        paged { count++ }
        tapAt(0.5f)
        assertEquals(1, count)
        tapAt(0.1f)
        tapAt(0.29f)
        tapAt(0.71f)
        tapAt(0.9f)
        assertEquals(1, count)
        tapAt(0.31f)
        assertEquals(2, count)
    }
}
