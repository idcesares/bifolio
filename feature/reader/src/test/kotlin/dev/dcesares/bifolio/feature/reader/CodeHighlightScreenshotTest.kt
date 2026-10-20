package dev.dcesares.bifolio.feature.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val CODE_PHONE = "w412dp-h915dp-xhdpi"
private const val CODE_WAIT_MILLIS = 20_000L

/** The kotlin and json fences of corpus C-08 (`code-languages.md`), under a short heading. */
private val codeDocument: Document by lazy {
    val source = File("../../testdata/corpus/code-languages.md").readText().replace("\r\n", "\n")
    val fences = Regex("```(kotlin|json)\n.*?\n```", RegexOption.DOT_MATCHES_ALL).findAll(source).map { it.value }
    MarkdownParser.parse("# Code\n\nA short kotlin and json sample.\n\n" + fences.joinToString("\n\n") + "\n")
}

/** SYN-05, SYN-11: colored tokens in a code block on the scroll and the paged surface, in the four themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeHighlightScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun scroll(
        theme: ReadingThemeId,
        name: String,
    ) {
        RuntimeEnvironment.setQualifiers("+$CODE_PHONE")
        val ready = ReaderUiState.Ready(codeDocument, "code", "hash-code", emptySet(), Anchor(0, 0))
        // Tokens are computed off the main thread while a block shows plain; the shot is of the warm cache.
        codeDocument.blocks.filterIsInstance<CodeBlock>().forEach { CodeTokenCache.spansOf(it.code, it.language) }
        compose.setContent { BifolioTheme(theme) { ReaderScreen(ready, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun paged(
        theme: ReadingThemeId,
        name: String,
    ) {
        RuntimeEnvironment.setQualifiers("+$CODE_PHONE")
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
                            if (layout?.request != request) layout = buildLayout(codeDocument, request)
                        },
                        onSettled = {},
                        onFirstDrawn = { drawn.set(true) },
                        onToggleChrome = {},
                        foldSource = foldSource,
                    )
                }
            }
        }
        compose.waitUntil(CODE_WAIT_MILLIS) {
            compose.onRoot().captureToImage()
            drawn.get()
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun scrollPaper() = scroll(ReadingThemeId.Paper, "code-highlight-scroll-paper-phone")

    @Test
    fun scrollSepia() = scroll(ReadingThemeId.Sepia, "code-highlight-scroll-sepia-phone")

    @Test
    fun scrollDusk() = scroll(ReadingThemeId.Dusk, "code-highlight-scroll-dusk-phone")

    @Test
    fun scrollNight() = scroll(ReadingThemeId.Night, "code-highlight-scroll-night-phone")

    @Test
    fun pagedPaper() = paged(ReadingThemeId.Paper, "code-highlight-paged-paper-phone")

    @Test
    fun pagedSepia() = paged(ReadingThemeId.Sepia, "code-highlight-paged-sepia-phone")

    @Test
    fun pagedDusk() = paged(ReadingThemeId.Dusk, "code-highlight-paged-dusk-phone")

    @Test
    fun pagedNight() = paged(ReadingThemeId.Night, "code-highlight-paged-night-phone")
}
