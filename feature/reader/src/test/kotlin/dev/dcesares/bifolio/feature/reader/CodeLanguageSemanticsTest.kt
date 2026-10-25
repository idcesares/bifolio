package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.SourceRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A11Y-04, scroll mode: a fenced code block announces its language, plain code does not. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeLanguageSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun stateOf(block: CodeBlock): String? {
        compose.setContent { BifolioTheme { CodeView(block.code, block = block) } }
        // The merged tree is what TalkBack walks: the state must sit on the node that carries the code text itself.
        val node = compose.onNodeWithText(block.code.trim(), substring = true).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.StateDescription)
    }

    @Test
    fun fencedCodeAnnouncesItsLanguage() {
        assertEquals("kotlin code", stateOf(CodeBlock("kotlin", "val x = 1", true, SourceRange(0, 9))))
    }

    @Test
    fun fencedCodeWithoutALanguageAnnouncesNothing() {
        assertEquals(null, stateOf(CodeBlock(null, "val y = 2", true, SourceRange(0, 9))))
    }

    @Test
    fun indentedCodeAnnouncesNothing() {
        assertEquals(null, stateOf(CodeBlock(null, "val z = 3", false, SourceRange(0, 9))))
    }
}
