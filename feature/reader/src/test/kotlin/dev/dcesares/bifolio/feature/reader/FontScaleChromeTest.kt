package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.data.ThemeChoice
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SLOT_AIR_PX = 8f

/** The text laid out by [this] node, or null when it is not a text node. */
internal fun SemanticsNode.textLayout(): TextLayoutResult? {
    val results = mutableListOf<TextLayoutResult>()
    val action = config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    return if (action(results)) results.firstOrNull() else null
}

/**
 * A11Y-03: nothing clips at 100%, 150% and 200% font scale. Every text of the tree is laid out without visual
 * overflow, and every node stays inside the window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FontScaleChromeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertNothingClips(label: String) {
        compose.waitForIdle()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val all = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
        val texts = all.mapNotNull { node -> node.textLayout()?.let { node to it } }
        assertTrue("$label: the chrome shows no text", texts.isNotEmpty())
        val failures =
            texts.mapNotNull { (node, layout) ->
                val words =
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        ?.joinToString { it.text }
                        .orEmpty()
                val box = node.boundsInRoot
                val slot =
                    generateSequence(
                        node.parent,
                    ) { it.parent }.firstOrNull { it.boundsInRoot != box }?.boundsInRoot
                val last = layout.lineCount - 1
                val cut = layout.getLineEnd(last, visibleEnd = false) < words.length
                // A little air at both ends: a glyph that touches the slot edge reads as clipped.
                val wide = layout.getLineRight(last) > (slot?.width ?: box.width) - SLOT_AIR_PX
                val outside =
                    slot != null &&
                        (
                            box.left < slot.left - 0.5f ||
                                box.right > slot.right + 0.5f ||
                                box.top < slot.top - 0.5f ||
                                box.bottom > slot.bottom + 0.5f
                        )
                if (cut || wide || outside) {
                    "'$words' cut=$cut wide=$wide outside=$outside line=${layout.getLineRight(
                        last,
                    )} box=$box slot=$slot"
                } else {
                    null
                }
            }
        assertTrue("$label: ${failures.joinToString(" | ")}", failures.isEmpty())
        all.forEach {
            val b = it.boundsInRoot
            assertTrue(
                "$label: node ${it.id} $b leaves the window $root",
                b.left >= root.left - 1 &&
                    b.right <= root.right + 1 &&
                    b.top >= root.top - 1 &&
                    b.bottom <= root.bottom + 1,
            )
        }
    }

    private fun chromeAt(
        fontScale: Float,
        widthDp: Int = 360,
        mode: ReadingMode = ReadingMode.SCROLL,
        theme: ThemeChoice? = null,
        shot: String? = null,
    ) {
        RuntimeEnvironment.setQualifiers("+w${widthDp}dp-h780dp-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(ReadingThemeId.Paper) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderChrome(
                            visible = true,
                            appearance = AppearanceProfile(theme = theme, columns = null),
                            mode = mode,
                        )
                    }
                }
            }
        }
        assertNothingClips("chrome at $fontScale, ${widthDp}dp, $mode, $theme")
        if (shot != null) compose.onRoot().captureRoboImage("src/test/screenshots/$shot.png")
    }

    @Test
    fun chromeFitsAtFontScaleOne() = chromeAt(1f)

    @Test
    fun chromeFitsAtFontScaleOneAndAHalf() = chromeAt(1.5f)

    @Test
    fun chromeFitsAtFontScaleTwo() = chromeAt(2f)

    @Test
    fun narrowestWindowFitsAtOneAndAHalfWithIconsOnly() =
        chromeAt(1.5f, 320, ReadingMode.SCROLL, ThemeChoice.SEPIA, "chrome-narrow-font150")

    @Test
    fun narrowestWindowFitsAtTwoWithIconsOnly() =
        chromeAt(2f, 320, ReadingMode.SCROLL, ThemeChoice.SEPIA, "chrome-narrow-font200")

    @Test
    fun narrowestWindowFitsAtTwoInPagedMode() =
        chromeAt(2f, widthDp = 320, mode = ReadingMode.PAGED, theme = ThemeChoice.PAPER)

    private fun captionTexts(): List<String> =
        compose
            .onAllNodes(
                SemanticsMatcher("text") { it.config.contains(SemanticsProperties.Text) },
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.Text].map { part -> part.text } }

    @Test
    fun captionsStayAtOnePointTwoAndFit() {
        chromeAt(1.2f, widthDp = 320, mode = ReadingMode.SCROLL, theme = ThemeChoice.SEPIA)
        assertTrue(captionTexts().toString(), captionTexts().containsAll(listOf("Auto", "Sepia", "Scroll")))
    }

    @Test
    fun captionsLeaveAboveOnePointTwoAndTheDescriptionsKeepTheValues() {
        chromeAt(1.5f, widthDp = 360, mode = ReadingMode.SCROLL, theme = ThemeChoice.SEPIA)
        val shown = captionTexts()
        assertTrue(shown.toString(), shown.none { it in listOf("Auto", "Sepia", "Scroll") })
        compose.onNodeWithContentDescription("Columns: Auto", substring = true).assertExists()
        compose.onNodeWithContentDescription("Sepia", substring = true).assertExists()
        compose.onNodeWithContentDescription("Scroll", substring = true).assertExists()
    }

    @Test
    fun justAboveTheThresholdTheCaptionsAreGoneAtTheNarrowestWidth() {
        chromeAt(1.3f, widthDp = 320, mode = ReadingMode.SCROLL, theme = ThemeChoice.SEPIA)
        assertTrue(captionTexts().toString(), captionTexts().none { it in listOf("Auto", "Sepia", "Scroll") })
        compose.onNodeWithContentDescription("Columns: Auto", substring = true).assertExists()
    }
}
