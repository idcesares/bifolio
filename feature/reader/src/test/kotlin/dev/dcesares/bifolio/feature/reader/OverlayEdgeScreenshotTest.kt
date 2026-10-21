package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.DocumentSearch
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

private const val EDGE_PHONE = "w412dp-h915dp-xhdpi"

/**
 * UI-07 and UI-08: the phone way into the table of contents, and the rule-colored edge of the table of contents
 * and search panels (DESIGN section 5), on Night where it matters and on Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class OverlayEdgeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready: ReaderUiState.Ready by lazy {
        val text = File("../../testdata/corpus/headings-all-levels.md").readText().replace("\r\n", "\n")
        ReaderUiState.Ready(MarkdownParser.parse(text), "edge", "hash-edge", emptySet(), Anchor(0, 0))
    }

    private val contents by lazy { TableOfContents(ready.document.blocks) }

    private fun show(
        theme: ReadingThemeId,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("+$EDGE_PHONE")
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderScreen(ready, {})
                        content()
                    }
                }
            }
        }
    }

    private fun toc() =
        @Composable {
            TocSheet(TocState(contents, 7, {}, {}), visible = true)
        }

    private fun search() =
        @Composable {
            val result = DocumentSearch.search(ready.document, "level")
            val state = SearchState(SearchPanel.List, "level", SearchPhase.Done, result, 0)
            SearchSheet(state, SearchActions({}, {}, {}, {}, {}, {}, {}), visible = true, autoFocus = false)
        }

    private fun tocButton(onClick: () -> Unit = {}) =
        @Composable {
            Box(Modifier.fillMaxSize()) {
                TocButton(
                    visible = true,
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.L, bottom = Spacing.Xl),
                )
            }
        }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun tocSheetEdgeNight() {
        show(ReadingThemeId.Night, toc())
        shot("toc-sheet-edge-night-phone")
    }

    @Test
    fun tocSheetEdgePaper() {
        show(ReadingThemeId.Paper, toc())
        shot("toc-sheet-edge-paper-phone")
    }

    @Test
    fun searchSheetEdgeNight() {
        show(ReadingThemeId.Night, search())
        shot("search-sheet-edge-night-phone")
    }

    @Test
    fun searchSheetEdgePaper() {
        show(ReadingThemeId.Paper, search())
        shot("search-sheet-edge-paper-phone")
    }

    @Test
    fun tocButtonNight() {
        show(ReadingThemeId.Night, tocButton())
        shot("toc-button-night-phone")
    }

    @Test
    fun tocButtonPaper() {
        show(ReadingThemeId.Paper, tocButton())
        shot("toc-button-paper-phone")
    }

    @Test
    fun tocButtonIsATouchTargetWithALabelAndOpens() {
        var opened = 0
        show(ReadingThemeId.Paper, tocButton { opened++ })
        compose.onNodeWithTag(TOC_BUTTON_TAG).assertWidthIsAtLeast(Spacing.TouchTarget)
        compose.onNodeWithTag(TOC_BUTTON_TAG).assertHeightIsAtLeast(Spacing.TouchTarget)
        compose.onNodeWithContentDescription("Table of contents").performClick()
        assertEquals(1, opened)
    }
}
