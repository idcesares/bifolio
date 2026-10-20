package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
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
import java.io.File

private const val PHONE = "w412dp-h915dp-xhdpi"
private const val TABLET = "w1280dp-h900dp-xhdpi"
private const val MARKER_LEFT = 500f
private const val MARKER_TOP = 420f
private const val MARKER_SIDE = 40f

/** UI-09: the footnote card as a bottom sheet on a phone and as a popover next to the marker from 840dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FootnotePopoverScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready: ReaderUiState.Ready by lazy {
        val text = File("../../testdata/corpus/footnotes.md").readText().replace("\r\n", "\n")
        ReaderUiState.Ready(MarkdownParser.parse(text), "notes", "hash-notes", emptySet(), Anchor(0, 0))
    }

    private fun show(
        qualifiers: String,
        theme: ReadingThemeId,
        label: String,
        fontScale: Float = 1f,
        goTo: MutableList<Unit> = mutableListOf(),
        dismissed: MutableList<Unit> = mutableListOf(),
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        val definition = checkNotNull(LinkResolver(ready.document.blocks).definitionOf(label))
        val bounds = Rect(MARKER_LEFT, MARKER_TOP, MARKER_LEFT + MARKER_SIDE, MARKER_TOP + MARKER_SIDE)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderScreen(ready, {})
                        FootnotePopover(
                            NoteCard(
                                NoteTap(label, bounds),
                                definition,
                                onGoTo = { goTo += Unit },
                                onDismiss = { dismissed += Unit },
                                onLink = {},
                                onNote = {},
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun sheetPaperPhone() {
        show(PHONE, ReadingThemeId.Paper, "patience")
        shot("footnote-sheet-paper-phone")
    }

    @Test
    fun sheetNightPhone() {
        show(PHONE, ReadingThemeId.Night, "patience")
        shot("footnote-sheet-night-phone")
    }

    @Test
    fun sheetPaperPhoneFont200() {
        show(PHONE, ReadingThemeId.Paper, "short", fontScale = 2f)
        shot("footnote-sheet-paper-phone-font200")
    }

    @Test
    fun popoverPaperTablet() {
        show(TABLET, ReadingThemeId.Paper, "patience")
        shot("footnote-popover-paper-tablet")
    }

    @Test
    fun popoverNightTablet() {
        show(TABLET, ReadingThemeId.Night, "short")
        shot("footnote-popover-night-tablet")
    }

    @Test
    fun theCardIsShownWithGoToAndClose() {
        show(PHONE, ReadingThemeId.Paper, "short")
        compose.onNodeWithTag(NOTE_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag(NOTE_GO_TO_TAG).assertIsDisplayed()
        compose.onNodeWithTag(NOTE_CLOSE_TAG).assertIsDisplayed()
    }

    @Test
    fun goToNoteAndCloseReportTheirIntent() {
        val goTo = mutableListOf<Unit>()
        val dismissed = mutableListOf<Unit>()
        show(PHONE, ReadingThemeId.Paper, "short", goTo = goTo, dismissed = dismissed)
        compose.onNodeWithTag(NOTE_GO_TO_TAG).performClick()
        compose.onNodeWithTag(NOTE_CLOSE_TAG).performClick()
        assertEquals(1, goTo.size)
        assertEquals(1, dismissed.size)
    }

    @Test
    fun escapeDismisses() {
        val dismissed = mutableListOf<Unit>()
        show(PHONE, ReadingThemeId.Paper, "short", dismissed = dismissed)
        compose.onNodeWithTag(NOTE_CARD_TAG).performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, dismissed.size)
    }
}
