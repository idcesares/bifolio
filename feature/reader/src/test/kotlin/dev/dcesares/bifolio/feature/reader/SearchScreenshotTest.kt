package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.DocumentSearch
import dev.dcesares.bifolio.layout.SearchHighlighter
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean

private const val SEARCH_PHONE = "w412dp-h915dp-xhdpi"
private const val SEARCH_TABLET = "w1280dp-h900dp-xhdpi"
private const val SEARCH_WAIT_MILLIS = 20_000L
private const val QUERY = "the"
private const val CURRENT = 2

/** UI-08: the search sheet and pane over the page, and the hits painted on the paged and scroll surfaces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SearchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val document: Document by lazy { parsed("prose-short") }

    private fun stateFor(
        query: String = QUERY,
        current: Int = CURRENT,
    ): SearchState {
        val result = DocumentSearch.search(document, query)
        return SearchState(SearchPanel.List, query, SearchPhase.Done, result, current)
    }

    private val ready: ReaderUiState.Ready
        get() = ReaderUiState.Ready(document, "search", "hash-search", emptySet(), Anchor(0, 0))

    private fun show(
        qualifiers: String,
        theme: ReadingThemeId,
        state: SearchState,
        fontScale: Float = 1f,
        log: MutableList<String> = mutableListOf(),
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        val actions =
            SearchActions(
                onQuery = { log += "query:$it" },
                onSelect = { log += "select:$it" },
                onNext = { log += "next" },
                onPrevious = { log += "previous" },
                onShowList = { log += "list" },
                onBack = { log += "back" },
                onClose = { log += "close" },
            )
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    Box(Modifier.fillMaxSize()) {
                        val highlighter =
                            remember(state) {
                                SearchHighlighter(document, state.query, state.result, state.current)
                            }
                        ReaderScreen(ready, {}, searchHighlighter = highlighter)
                        if (state.panel == SearchPanel.Bar) {
                            SearchBar(state, actions, visible = true, modifier = Modifier.align(Alignment.BottomCenter))
                        } else {
                            SearchSheet(state, actions, visible = true, autoFocus = false)
                        }
                    }
                }
            }
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun sheetPaperPhone() {
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor())
        shot("search-sheet-paper-phone")
    }

    @Test
    fun sheetNightPhone() {
        show(SEARCH_PHONE, ReadingThemeId.Night, stateFor())
        shot("search-sheet-night-phone")
    }

    @Test
    fun sheetPaperPhoneFont200() {
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor(), fontScale = 2f)
        shot("search-sheet-paper-phone-font200")
    }

    @Test
    fun panePaperTablet() {
        show(SEARCH_TABLET, ReadingThemeId.Paper, stateFor())
        shot("search-pane-paper-tablet")
    }

    @Test
    fun paneNightTablet() {
        show(SEARCH_TABLET, ReadingThemeId.Night, stateFor())
        shot("search-pane-night-tablet")
    }

    @Test
    fun idlePaperPhone() {
        show(SEARCH_PHONE, ReadingThemeId.Paper, SearchState(SearchPanel.List))
        shot("search-sheet-idle-paper-phone")
    }

    /** NAV-03: Escape inside the open sheet, with the field focused or not, asks to go back one level. */
    @Test
    fun escapeInsideTheSheetGoesBack() {
        val log = mutableListOf<String>()
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor(), log = log)
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performClick()
        compose.onNodeWithTag(SEARCH_FIELD_TAG).performKeyInput { pressKey(Key.Escape) }
        assertEquals(listOf("back"), log)
    }

    @Test
    fun noResultsPaperPhone() {
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor("zzzz", NO_RESULT))
        shot("search-sheet-noresult-paper-phone")
    }

    @Test
    fun barNightPhone() {
        show(SEARCH_PHONE, ReadingThemeId.Night, stateFor().copy(panel = SearchPanel.Bar))
        shot("search-bar-night-phone")
    }

    @Test
    fun barPaperPhone() {
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor().copy(panel = SearchPanel.Bar))
        shot("search-bar-paper-phone")
    }

    @Test
    fun atEightHundredFortyDpItIsASidePaneAndBelowItIsASheet() {
        show("w840dp-h900dp-xhdpi", ReadingThemeId.Paper, stateFor())
        compose.onNodeWithTag(SEARCH_SHEET_TAG).assertWidthIsEqualTo(Spacing.SidePaneWidth)
    }

    @Test
    fun belowEightHundredFortyDpItIsASheetAtMostFiveHundredSixtyWide() {
        show("w839dp-h900dp-xhdpi", ReadingThemeId.Paper, stateFor())
        compose.onNodeWithTag(SEARCH_SHEET_TAG).assertWidthIsEqualTo(Spacing.SheetMaxWidth)
    }

    @Test
    fun rowsAreTouchTargetsAndTheCurrentOneIsSelected() {
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor())
        compose.onAllNodesWithTag(SEARCH_ROW_TAG).onFirst().assertHeightIsAtLeast(Spacing.TouchTarget)
        assertEquals(1, compose.onAllNodes(isSelected()).fetchSemanticsNodes().size)
        compose.onNodeWithTag(SEARCH_NEXT_TAG).assertHeightIsAtLeast(Spacing.TouchTarget)
        compose.onNodeWithTag(SEARCH_PREVIOUS_TAG).assertHeightIsAtLeast(Spacing.TouchTarget)
    }

    @Test
    fun theControlsReportTheirActions() {
        val log = mutableListOf<String>()
        show(SEARCH_PHONE, ReadingThemeId.Paper, stateFor(), log = log)
        compose.onNodeWithContentDescription("Next result").performClick()
        compose.onNodeWithContentDescription("Previous result").performClick()
        compose.onAllNodesWithTag(SEARCH_ROW_TAG)[1].performClick()
        compose.onNodeWithContentDescription("Close search").performClick()
        assertEquals(listOf("next", "previous", "select:1", "close"), log)
    }
}

/** UI-08: hits painted under the text of the paged surface, current result outlined. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PagedSearchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun paged(
        theme: ReadingThemeId,
        shotName: String,
    ) {
        RuntimeEnvironment.setQualifiers("+$SEARCH_PHONE")
        val document = parsed("prose-short")
        val result = DocumentSearch.search(document, QUERY)
        val highlighter = SearchHighlighter(document, QUERY, result, CURRENT)
        val drawn = AtomicBoolean(false)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    var layout by remember { mutableStateOf<PagedLayout?>(null) }
                    val foldSource = remember { FakeFoldSource(null) }
                    PagedSurface(
                        layout = layout,
                        initialAnchor = Anchor(0, 0),
                        onRequest = { request ->
                            if (layout?.request != request) layout = buildLayout(document, request)
                        },
                        onSettled = {},
                        onFirstDrawn = { drawn.set(true) },
                        onToggleChrome = {},
                        foldSource = foldSource,
                        searchHighlighter = highlighter,
                    )
                }
            }
        }
        compose.waitUntil(SEARCH_WAIT_MILLIS) {
            compose.onRoot().captureToImage()
            drawn.get()
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$shotName.png")
    }

    @Test
    fun hitsPaperPhone() = paged(ReadingThemeId.Paper, "search-paged-hits-paper-phone")

    @Test
    fun hitsNightPhone() = paged(ReadingThemeId.Night, "search-paged-hits-night-phone")
}
