package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.designsystem.Spacing
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
private const val COLLAPSED_CURRENT = 7
private const val DEEP_CURRENT = 5

/** UI-07: the table of contents as a bottom sheet on a phone and as a side pane from 840dp, over the page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class TocScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready: ReaderUiState.Ready by lazy {
        val text = File("../../testdata/corpus/headings-all-levels.md").readText().replace("\r\n", "\n")
        val document = MarkdownParser.parse(text)
        ReaderUiState.Ready(document, "toc", "hash-toc", emptySet(), Anchor(0, 0))
    }

    private val contents by lazy { TableOfContents(ready.document.blocks) }

    private fun show(
        qualifiers: String,
        theme: ReadingThemeId,
        current: Int,
        fontScale: Float = 1f,
        selected: MutableList<TocEntry> = mutableListOf(),
        dismissed: MutableList<Unit> = mutableListOf(),
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            // Reduced motion: the panel is fully in place when the screenshot is taken.
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderScreen(ready, {})
                        TocSheet(
                            state = TocState(contents, current, { selected += it }, { dismissed += Unit }),
                            visible = true,
                        )
                    }
                }
            }
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun sheetPaperPhone() {
        show(PHONE, ReadingThemeId.Paper, COLLAPSED_CURRENT)
        shot("toc-sheet-paper-phone")
    }

    @Test
    fun sheetNightPhoneDeepCurrent() {
        show(PHONE, ReadingThemeId.Night, DEEP_CURRENT)
        shot("toc-sheet-night-phone")
    }

    @Test
    fun sheetPaperPhoneFont200() {
        show(PHONE, ReadingThemeId.Paper, COLLAPSED_CURRENT, fontScale = 2f)
        shot("toc-sheet-paper-phone-font200")
    }

    @Test
    fun paneSepiaTablet() {
        show(TABLET, ReadingThemeId.Sepia, COLLAPSED_CURRENT)
        shot("toc-pane-sepia-tablet")
    }

    @Test
    fun paneNightTablet() {
        show(TABLET, ReadingThemeId.Night, DEEP_CURRENT)
        shot("toc-pane-night-tablet")
    }

    @Test
    fun atEightHundredFortyDpItIsASidePaneAndBelowItIsASheet() {
        show("w840dp-h900dp-xhdpi", ReadingThemeId.Paper, COLLAPSED_CURRENT)
        compose.onNodeWithTag(TOC_SHEET_TAG).assertWidthIsEqualTo(Spacing.SidePaneWidth)
    }

    @Test
    fun belowEightHundredFortyDpItIsASheetAtMostFiveHundredSixtyWide() {
        show("w839dp-h900dp-xhdpi", ReadingThemeId.Paper, COLLAPSED_CURRENT)
        compose.onNodeWithTag(TOC_SHEET_TAG).assertWidthIsEqualTo(Spacing.SheetMaxWidth)
    }

    @Test
    fun rowsAreTouchTargetsAndTheCurrentOneIsSelected() {
        show(PHONE, ReadingThemeId.Paper, COLLAPSED_CURRENT)
        compose.onAllNodesWithTag(TOC_ROW_TAG).onFirst().assertHeightIsAtLeast(Spacing.TouchTarget)
        assertEquals(1, compose.onAllNodes(isSelected()).fetchSemanticsNodes().size)
    }

    @Test
    fun choosingAnEntryReportsItsAnchor() {
        val selected = mutableListOf<TocEntry>()
        show(PHONE, ReadingThemeId.Paper, COLLAPSED_CURRENT, selected = selected)
        compose.onNode(hasText("Level two") and hasClickAction()).performClick()
        assertEquals(listOf(contents.entries[1].anchor), selected.map { it.anchor })
    }

    @Test
    fun deeperHeadingsOpenOnRequest() {
        show(PHONE, ReadingThemeId.Paper, COLLAPSED_CURRENT)
        val before = compose.onAllNodesWithTag(TOC_ROW_TAG).fetchSemanticsNodes().size
        compose.onNodeWithContentDescription("Show 3 deeper headings").performClick()
        assertEquals(before + 3, compose.onAllNodesWithTag(TOC_ROW_TAG).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Hide 3 deeper headings").assertExists()
    }

    @Test
    fun theScrimAndTheCloseButtonDismiss() {
        val dismissed = mutableListOf<Unit>()
        show(PHONE, ReadingThemeId.Paper, COLLAPSED_CURRENT, dismissed = dismissed)
        compose.onNodeWithContentDescription("Close contents").performClick()
        assertEquals(1, dismissed.size)
    }
}
