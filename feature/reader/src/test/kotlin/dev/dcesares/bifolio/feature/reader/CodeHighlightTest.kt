package dev.dcesares.bifolio.feature.reader

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.isUnspecified
import dev.dcesares.bifolio.designsystem.ReadingPalettes
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.highlight.TokenKind
import dev.dcesares.bifolio.layout.DrawText
import dev.dcesares.bifolio.layout.TextLeaf
import dev.dcesares.bifolio.layout.TextRole
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser
import dev.dcesares.bifolio.text.ColorRuns
import dev.dcesares.bifolio.text.TextLayoutFactory
import dev.dcesares.bifolio.text.TextPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val REQUEST = PageRequest(824, 1830, 2f, 1f, 17f, "en")

/** SYN-05, SYN-11: syntax colors are paint only and never move a line. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeHighlightTest {
    private fun codeLeaves(
        document: Document,
        request: PageRequest = REQUEST,
    ): Pair<List<TextLeaf>, TextLayoutFactory> {
        val layout = buildLayout(document, request)
        val leaves = LinkedHashSet<TextLeaf>()
        for (page in layout.flow.pages) {
            for (item in layout.detailer.detail(page, 0).items) {
                if (item is DrawText && item.leaf.role == TextRole.CODE) leaves += item.leaf
            }
        }
        return leaves.toList() to TextLayoutFactory(layout.config)
    }

    private fun textPalette() =
        ReadingPalettes.Paper.let {
            TextPalette(
                it.ink.toArgb(),
                it.inkMuted.toArgb(),
                it.link.toArgb(),
                it.accent.toArgb(),
                it.accent.toArgb(),
                it.codeSurface.toArgb(),
            )
        }

    @Test
    fun codeLongBlockBreaksIntoTheSameLinesWithAndWithoutHighlighting() {
        for (request in listOf(REQUEST, REQUEST.copy(fontScale = 2f), REQUEST.copy(widthPx = 600, bodySp = 20f))) {
            val (leaves, factory) = codeLeaves(parsed("code-long-block"), request)
            assertTrue(leaves.isNotEmpty())
            for (theme in ReadingThemeId.entries) {
                val tokens = ReadingPalettes.of(theme).codeTokens
                for (leaf in leaves) {
                    val spans = checkNotNull(CodeTokenCache.spansOf(leaf.text, leaf.language))
                    val runs = colorRunsOf(spans, tokens)
                    assertNotNull(runs)
                    assertSameLines(factory, leaf, runs)
                }
            }
        }
    }

    private fun assertSameLines(
        factory: TextLayoutFactory,
        leaf: TextLeaf,
        runs: ColorRuns?,
    ) {
        val plain = factory.layout(leaf.text, leaf.role, leaf.width, leaf.styles, textPalette())
        val painted = factory.layout(leaf.text, leaf.role, leaf.width, leaf.styles, textPalette(), runs)
        assertEquals(plain.lineCount, painted.lineCount)
        assertEquals(plain.height, painted.height)
        for (i in 0 until plain.lineCount) {
            assertEquals(plain.getLineStart(i), painted.getLineStart(i))
            assertEquals(plain.getLineEnd(i), painted.getLineEnd(i))
            assertEquals(plain.getLineTop(i), painted.getLineTop(i))
            assertEquals(plain.getLineBottom(i), painted.getLineBottom(i))
            assertEquals(plain.getLineWidth(i), painted.getLineWidth(i), 0f)
        }
        val colored = (painted.text as Spanned).getSpans(0, leaf.text.length, ForegroundColorSpan::class.java)
        assertTrue(colored.isNotEmpty())
    }

    @Test
    fun paginationDoesNotDependOnTheTokenizer() {
        // The paginator and detailer take no highlighter: pages with a warm and a cold token cache are the same.
        val document = parsed("code-long-block")
        val cold = buildLayout(document, REQUEST).flow.pages
        codeLeaves(document).first.forEach { CodeTokenCache.spansOf(it.text, it.language) }
        val warm = buildLayout(document, REQUEST).flow.pages
        assertEquals(cold, warm)
    }

    @Test
    fun onlyFencedCodeWithALanguageIsColored() {
        val document =
            MarkdownParser.parse(
                "```kotlin\nval a = 1\n```\n\n```mermaid\ngraph TD\n  A --> B\n```\n\n" +
                    "```nolang-xyz\nplain text\n```\n\n    indented code\n",
            )
        val (leaves, _) = codeLeaves(document)
        assertEquals(listOf("kotlin", null, "nolang-xyz", null), leaves.map { it.language })
        assertNotNull(CodeTokenCache.spansOf(leaves[0].text, leaves[0].language))
        leaves.drop(1).forEach { assertNull(CodeTokenCache.spansOf(it.text, it.language)) }
    }

    @Test
    fun tokensTakeTheirColorFromThePaletteOfTheTheme() {
        val code = "fun main() { println(\"hi\") }"
        val spans = checkNotNull(CodeTokenCache.spansOf(code, "kotlin"))
        for (theme in ReadingThemeId.entries) {
            val tokens = ReadingPalettes.of(theme).codeTokens
            val runs = checkNotNull(colorRunsOf(spans, tokens))
            assertEquals(tokens.keyword.toArgb(), runs.colors[runs.starts.indexOf(0)])
            val string = spans.first { it.kind == TokenKind.STRING }
            assertEquals(tokens.string.toArgb(), runs.colors[runs.starts.indexOf(string.start)])
        }
    }

    @Test
    fun theScrollSurfaceKeepsFontAndWeightOfPlainCode() {
        val code = "val a = 1 // note"
        val spans = checkNotNull(CodeTokenCache.spansOf(code, "kotlin"))
        val text = codeAnnotated(code, spans, ReadingPalettes.Night.codeTokens)
        assertEquals(code, text.text)
        assertTrue(text.spanStyles.isNotEmpty())
        text.spanStyles.forEach {
            assertNull(it.item.fontWeight)
            assertNull(it.item.fontStyle)
            assertNull(it.item.fontFamily)
            assertTrue(it.item.fontSize.isUnspecified)
        }
    }

    @Test
    fun cachedSpansAreTheSameObjectForTheSameBlock() {
        val code = "val b = 2"
        assertTrue(CodeTokenCache.spansOf(code, "kotlin") === CodeTokenCache.spansOf(code, "kotlin"))
    }
}
