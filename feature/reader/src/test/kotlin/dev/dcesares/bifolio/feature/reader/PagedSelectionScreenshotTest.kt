package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.model.Anchor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean

private const val PHONE = "w412dp-h915dp-xhdpi"
private const val WAIT_MILLIS = 20_000L

/** NAV-09 on the paged surface: a long press selects a word, handles and the Copy and Share bar appear. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PagedSelectionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val turns = mutableListOf<Unit>()
    private val chromeToggles = mutableListOf<Unit>()
    private val links = mutableListOf<String>()

    private fun open(
        theme: ReadingThemeId,
        fontScale: Float = 1f,
        file: String = "prose-short",
    ) {
        RuntimeEnvironment.setQualifiers("+$PHONE")
        RuntimeEnvironment.setFontScale(fontScale)
        val document = parsed(file)
        val drawn = AtomicBoolean(false)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    var layout by remember { mutableStateOf<PagedLayout?>(null) }
                    Box(Modifier.fillMaxSize()) {
                        PagedSurface(
                            layout = layout,
                            initialAnchor = Anchor(0, 0),
                            onRequest = { request ->
                                if (layout?.request !=
                                    request
                                ) {
                                    layout = buildLayout(document, request)
                                }
                            },
                            onSettled = { turns += Unit },
                            onFirstDrawn = { drawn.set(true) },
                            onToggleChrome = { chromeToggles += Unit },
                            onLink = { links += it },
                        )
                    }
                }
            }
        }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onRoot().captureToImage()
            drawn.get()
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    // Over the word "because" in the first paragraph of the corpus text, in px at xhdpi.
    private val onWord = Offset(300f, 380f)

    @Test
    fun longPressSelectsAWordWithHandlesPaper() {
        open(ReadingThemeId.Paper)
        compose.onRoot().performTouchInput { longClick(onWord) }
        compose.waitForIdle()
        compose.onNodeWithTag(SELECTION_BAR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SELECTION_HANDLE_START_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SELECTION_HANDLE_END_TAG).assertIsDisplayed()
        shot("paged-selection-paper-phone")
    }

    @Test
    fun longPressSelectsAWordWithHandlesNight() {
        open(ReadingThemeId.Night)
        compose.onRoot().performTouchInput { longClick(onWord) }
        compose.waitForIdle()
        shot("paged-selection-night-phone")
    }

    @Test
    fun selectionBarAtFont200StaysOnScreen() {
        open(ReadingThemeId.Paper, fontScale = 2f)
        compose.onRoot().performTouchInput { longClick(Offset(300f, 600f)) }
        compose.waitForIdle()
        shot("paged-selection-paper-phone-font200")
    }

    private fun tapAt(point: Offset) {
        compose.onRoot().performTouchInput { click(point) }
        compose.waitForIdle()
    }

    @Test
    fun aCenterTapOnlyLetsGoOfTheSelectionAndDoesNotToggleTheChrome() {
        open(ReadingThemeId.Paper)
        // Control: without a selection the same tap toggles the chrome.
        tapAt(Offset(412f, 1500f))
        assertEquals(1, chromeToggles.size)
        compose.onRoot().performTouchInput { longClick(onWord) }
        compose.waitForIdle()
        compose.onNodeWithTag(SELECTION_BAR_TAG).assertIsDisplayed()
        tapAt(Offset(412f, 1500f))
        compose.onAllNodesWithTag(SELECTION_BAR_TAG).assertCountEquals(0)
        assertEquals(1, chromeToggles.size)
    }

    @Test
    fun anOuterTapWithASelectionDoesNotTurnThePage() {
        open(ReadingThemeId.Paper, fontScale = 2f, file = "headings-all-levels")
        compose.onRoot().performTouchInput { longClick(Offset(300f, 700f)) }
        compose.waitForIdle()
        compose.onNodeWithTag(SELECTION_BAR_TAG).assertIsDisplayed()
        tapAt(Offset(780f, 1500f))
        compose.onAllNodesWithTag(SELECTION_BAR_TAG).assertCountEquals(0)
        assertEquals(0, turns.size)
        // Control: the same tap with nothing selected turns the page.
        tapAt(Offset(780f, 1500f))
        assertEquals(1, turns.size)
    }

    @Test
    fun aLongPressOnBlankPageSelectsNothing() {
        open(ReadingThemeId.Paper)
        compose.onRoot().performTouchInput { longClick(Offset(400f, 1700f)) }
        compose.waitForIdle()
        compose.onAllNodesWithTag(SELECTION_BAR_TAG).assertCountEquals(0)
    }
}
