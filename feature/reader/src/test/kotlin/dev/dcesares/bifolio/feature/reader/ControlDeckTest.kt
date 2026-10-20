package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.FoldOcclusion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val DECK_QUALIFIERS = "w752dp-h835dp-xxhdpi"
private const val WAIT_MILLIS = 20_000L
private const val DECK_PREVIOUS = "Previous page"
private const val DECK_NEXT = "Next page"
private const val MID = 0.5f

/** POS-04 and NAV-02: the control deck of the tabletop posture, alone and on the paged surface. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ControlDeckTest {
    @get:Rule
    val compose = createComposeRule()

    private class Calls {
        var previous = 0
        var next = 0
        val seeks = mutableListOf<Float>()
        var finished = 0
    }

    private fun deck(
        calls: Calls,
        height: Int = DECK_HEIGHT_DP,
        progress: Float = 0.25f,
    ) {
        RuntimeEnvironment.setQualifiers("+$DECK_QUALIFIERS")
        compose.setContent {
            BifolioTheme {
                Box(Modifier.size(400.dp, height.dp)) {
                    ControlDeck(
                        progress = progress,
                        onPrevious = { calls.previous++ },
                        onNext = { calls.next++ },
                        onSeek = { calls.seeks += it },
                        onSeekFinished = { calls.finished++ },
                    )
                }
            }
        }
    }

    @Test
    fun previousAndNextZonesCallThePagingActions() {
        val calls = Calls()
        deck(calls)
        compose.onNodeWithContentDescription(DECK_PREVIOUS).performClick()
        assertEquals(1, calls.previous)
        assertEquals(0, calls.next)
        compose.onNodeWithContentDescription(DECK_NEXT).performClick()
        compose.onNodeWithContentDescription(DECK_NEXT).performClick()
        assertEquals(1, calls.previous)
        assertEquals(2, calls.next)
    }

    @Test
    fun draggingTheScrubberSeeksForwardAndFinishesOnce() {
        val calls = Calls()
        deck(calls)
        compose.onNodeWithTag(DECK_SCRUBBER_TAG).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue("no seek reported", calls.seeks.isNotEmpty())
        assertEquals("seeks must grow while dragging right", calls.seeks.sorted(), calls.seeks)
        assertTrue("drag must end near the end: ${calls.seeks.last()}", calls.seeks.last() > 0.9f)
        assertTrue("drag must start near the start: ${calls.seeks.first()}", calls.seeks.first() < 0.2f)
        assertEquals(1, calls.finished)
    }

    @Test
    fun setProgressActionSeeksAndFinishes() {
        val calls = Calls()
        deck(calls)
        compose
            .onNodeWithTag(DECK_SCRUBBER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(MID) }
        assertEquals(listOf(MID), calls.seeks)
        assertEquals(1, calls.finished)
    }

    @Test
    fun fitsTheMinimumDeckOfTwoTouchTargets() {
        val calls = Calls()
        deck(calls, height = (Spacing.TouchTarget.value * 2).toInt())
        compose.onNodeWithTag(DECK_PREVIOUS_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(DECK_NEXT_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(DECK_SCRUBBER_TAG).assertIsDisplayed().assertHeightIsAtLeast(Spacing.TouchTarget)
        compose.onNodeWithContentDescription(DECK_PREVIOUS).assertHeightIsAtLeast(Spacing.TouchTarget)
        compose.onNodeWithContentDescription(DECK_NEXT).assertHeightIsAtLeast(Spacing.TouchTarget)
        compose.onNodeWithContentDescription("Table of contents").assertHeightIsAtLeast(Spacing.TouchTarget)
    }

    /** A harness without a contents opener (previews) shows the button disabled. */
    @Test
    fun tableOfContentsButtonIsDisabledWithoutAnOpener() {
        deck(Calls())
        compose.onNodeWithContentDescription("Table of contents").assertIsDisplayed().assertIsNotEnabled()
    }

    /** UI-07: no headings, no button. */
    @Test
    fun tableOfContentsButtonIsAbsentWhenTheDocumentHasNoHeadings() {
        RuntimeEnvironment.setQualifiers("+$DECK_QUALIFIERS")
        compose.setContent {
            BifolioTheme {
                Box(Modifier.size(400.dp, DECK_HEIGHT_DP.dp)) {
                    ControlDeck(0.5f, {}, {}, {}, {}, onOpenToc = {}, showToc = false)
                }
            }
        }
        assertEquals(0, compose.onAllNodesWithContentDescription("Table of contents").fetchSemanticsNodes().size)
    }

    @Test
    fun tableOfContentsButtonOpensTheContents() {
        var opened = 0
        RuntimeEnvironment.setQualifiers("+$DECK_QUALIFIERS")
        compose.setContent {
            BifolioTheme {
                Box(Modifier.size(400.dp, DECK_HEIGHT_DP.dp)) {
                    ControlDeck(0.5f, {}, {}, {}, {}, onOpenToc = { opened++ })
                }
            }
        }
        compose.onNodeWithContentDescription("Table of contents").performClick()
        assertEquals(1, opened)
    }

    private val tabletopFold = { win: Win ->
        FoldSpec(
            0,
            win.height / 2,
            win.width,
            win.height / 2,
            true,
            FoldOrientation.Horizontal,
            true,
            FoldOcclusion.None,
        )
    }

    private class Win(
        val width: Int,
        val height: Int,
    )

    private fun surface(
        fold: ((Win) -> FoldSpec)?,
        settled: MutableList<Anchor>,
    ): Document {
        RuntimeEnvironment.setQualifiers("+$DECK_QUALIFIERS")
        val metrics = RuntimeEnvironment.getApplication().resources.displayMetrics
        val spec = fold?.invoke(Win(metrics.widthPixels, metrics.heightPixels))
        val document = parsed("widows-orphans")
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme {
                    var layout by remember { mutableStateOf<PagedLayout?>(null) }
                    val source = remember { FakeFoldSource(spec) }
                    Box(Modifier.fillMaxSize()) {
                        PagedSurface(
                            layout = layout,
                            initialAnchor = Anchor(0, 0),
                            onRequest = { request ->
                                if (layout?.request != request) layout = buildLayout(document, request)
                            },
                            onSettled = { settled += it },
                            onFirstDrawn = {},
                            onToggleChrome = {},
                            foldSource = source,
                            document = document,
                        )
                    }
                }
            }
        }
        return document
    }

    @Test
    fun deckIsAbsentWhenThereIsNoDeckRegion() {
        surface(null, mutableListOf())
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag(DECK_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun deckIsPresentInTabletop() {
        surface(tabletopFold, mutableListOf())
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithTag(DECK_TAG).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(DECK_TAG).assertIsDisplayed()
    }

    @Test
    fun nextZoneTurnsTheSpreadAndSettlesOnAnAnchorAfterTheStart() {
        val settled = mutableListOf<Anchor>()
        surface(tabletopFold, settled)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithTag(DECK_TAG).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(DECK_NEXT).performClick()
        compose.waitUntil(WAIT_MILLIS) { settled.isNotEmpty() }
        val first = settled.first()
        assertTrue("next must move forward: $first", first.blockIndex > 0 || first.charOffset > 0)
    }

    @Test
    fun scrubberSeeksByAnchorAndSettlesOnceAtASpreadStart() {
        val settled = mutableListOf<Anchor>()
        val document = surface(tabletopFold, settled)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithTag(DECK_TAG).fetchSemanticsNodes().isNotEmpty() }
        compose
            .onNodeWithTag(DECK_SCRUBBER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(MID) }
        compose.waitUntil(WAIT_MILLIS) { settled.isNotEmpty() }
        assertEquals("one settle per drag", 1, settled.size)
        val offset = document.sourceOffsetOf(settled.single())
        val wanted = (MID * document.source.length).toInt()
        assertTrue("settled $offset must not pass the target $wanted", offset <= wanted)
        assertTrue("settled $offset must be in the middle part of the text", offset > wanted / 2)
    }

    private companion object {
        const val DECK_HEIGHT_DP = 200
    }
}
