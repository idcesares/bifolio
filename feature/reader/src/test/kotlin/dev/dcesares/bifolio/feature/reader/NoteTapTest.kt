package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.model.Anchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean

private const val WAIT = 20_000L

/** UI-09, NAV-07: a tap on a footnote marker reaches the handler on the paged and on the scroll surface. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NoteTapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aTapOnAPagedMarkerOpensItsNote() {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xhdpi")
        val document = parsed("footnotes")
        val taps = mutableListOf<NoteTap>()
        val drawn = AtomicBoolean(false)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(ReadingThemeId.Paper) {
                    var layout by remember { mutableStateOf<PagedLayout?>(null) }
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
                        onSettled = {},
                        onFirstDrawn = { drawn.set(true) },
                        onToggleChrome = {},
                        onNote = { taps += it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitUntil(WAIT) {
            compose.onRoot().captureToImage()
            drawn.get()
        }
        compose.waitForIdle()
        val marker = compose.onNodeWithContentDescription("Footnote patience")
        val node = marker.fetchSemanticsNode()
        val density =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        assertTrue(node.size.width >= Spacing.TouchTarget.value * density - 1)
        assertTrue(node.size.height >= Spacing.TouchTarget.value * density - 1)
        val center = node.boundsInRoot.center
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(listOf("patience"), taps.map { it.label })
    }

    @Test
    fun aMarkerOnTheScrollSurfaceIsAnActionThatOpensItsNote() {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xhdpi")
        val document = parsed("footnotes")
        val ready = ReaderUiState.Ready(document, "n", "h", emptySet(), Anchor(0, 0))
        val taps = mutableListOf<NoteTap>()
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(ReadingThemeId.Paper) { ReaderScreen(ready, {}, onNote = { taps += it }) }
            }
        }
        compose.waitForIdle()
        val label = "Footnote patience"
        val withAction =
            SemanticsMatcher("has custom action $label") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
            }
        val node = compose.onAllNodes(withAction).fetchSemanticsNodes().first()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
        assertEquals(listOf("patience"), taps.map { it.label })
    }
}
