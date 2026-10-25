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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ReadingMode
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
                val wide = layout.getLineRight(last) > box.width + 0.5f
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

    private fun chromeAt(fontScale: Float) {
        RuntimeEnvironment.setQualifiers("+w360dp-h780dp-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(ReadingThemeId.Paper) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderChrome(visible = true, appearance = AppearanceProfile(), mode = ReadingMode.SCROLL)
                    }
                }
            }
        }
        assertNothingClips("chrome at $fontScale")
    }

    @Test
    fun chromeFitsAtFontScaleOne() = chromeAt(1f)

    @Test
    fun chromeFitsAtFontScaleOneAndAHalf() = chromeAt(1.5f)

    @Test
    fun chromeFitsAtFontScaleTwo() = chromeAt(2f)
}
