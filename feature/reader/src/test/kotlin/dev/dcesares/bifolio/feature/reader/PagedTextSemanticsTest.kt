package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val WIDTH_PX = 1504
private const val HEIGHT_PX = 1670
private const val WAIT_MILLIS = 20_000L

/**
 * A11Y-01: the paged canvas exposes its text to TalkBack as nodes, in reading order, with headings marked. Which
 * order TalkBack really follows, and the spoken result, need a device; this checks what the app declares.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PagedTextSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val paragraph = "The quick brown fox jumps over the lazy dog. ".repeat(14)

    private val markdown =
        buildString {
            append("# Opening Heading\n\n")
            append("A claim worth a note.[^n] $paragraph\n\n")
            append("## Second Heading\n\n")
            append("$$\nE = mc^2\n$$\n\n")
            repeat(14) { append("Paragraph $it. $paragraph\n\n") }
            append("[^n]: The note.\n")
        }

    private fun show(source: String = markdown) {
        RuntimeEnvironment.setQualifiers("+w752dp-h835dp-xhdpi")
        val layout =
            buildLayout(
                MarkdownParser.parse(source),
                PageRequest(WIDTH_PX, HEIGHT_PX, 2f, 1f, 18f, "en", forcedColumns = 2),
            )
        compose.setContent {
            BifolioTheme {
                PagedSurface(
                    layout = layout,
                    initialAnchor = Anchor(0, 0),
                    onRequest = {},
                    onSettled = {},
                    onFirstDrawn = {},
                    onToggleChrome = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Columns are prepared off the main thread: the nodes appear once both are ready.
        compose.waitUntil(WAIT_MILLIS) {
            nodes(SemanticsProperties.Text).map { it.boundsInRoot.center.x < WIDTH_PX / 2f }.toSet().size == 2
        }
    }

    private fun nodes(key: androidx.compose.ui.semantics.SemanticsPropertyKey<*>): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(key)).fetchSemanticsNodes()

    private fun textOf(node: SemanticsNode): String =
        node.config
            .getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            .orEmpty()

    @Test
    fun headingsAreTextNodesMarkedAsHeadings() {
        show()
        val texts = nodes(SemanticsProperties.Text)
        val opening = texts.first { textOf(it).startsWith("Opening Heading") }
        val second = texts.first { textOf(it).startsWith("Second Heading") }
        val body = texts.first { textOf(it).startsWith("A claim worth a note") }
        assertTrue(opening.config.contains(SemanticsProperties.Heading))
        assertTrue(second.config.contains(SemanticsProperties.Heading))
        assertFalse(body.config.contains(SemanticsProperties.Heading))
    }

    @Test
    fun theFootnoteMarkerLabelIsNotSpokenInsideTheParagraph() {
        show()
        val body = nodes(SemanticsProperties.Text).first { textOf(it).startsWith("A claim worth a note") }
        assertTrue(textOf(body), textOf(body).startsWith("A claim worth a note. The quick"))
    }

    @Test
    fun nodesCarryAReadingOrderThatFinishesColumnOneBeforeColumnTwo() {
        show()
        val spoken =
            (
                nodes(SemanticsProperties.Text) +
                    nodes(SemanticsProperties.ContentDescription).filter {
                        !it.config.contains(SemanticsProperties.Text)
                    }
            ).filter { it.config.getOrNull(SemanticsProperties.TraversalIndex) != null }
        assertTrue("text, formula and note nodes need an index", spoken.size >= 6)
        val ordered = spoken.sortedBy { it.config[SemanticsProperties.TraversalIndex] }
        val half = WIDTH_PX / 2f
        val columns = ordered.map { if (it.boundsInRoot.center.x < half) 0 else 1 }
        assertEquals("both columns carry text", setOf(0, 1), columns.toSet())
        assertEquals("column one is read first, then column two", columns.sorted(), columns)
        val firstColumn = ordered.filterIndexed { i, _ -> columns[i] == 0 }
        assertEquals(
            "inside a column the order follows the page",
            firstColumn.map { it.boundsInRoot.top }.sorted().first(),
            firstColumn.first().boundsInRoot.top,
            1f,
        )
        // The second column starts at the top of the page, higher than the end of the first one.
        val secondColumn = ordered.filterIndexed { i, _ -> columns[i] == 1 }
        assertTrue(secondColumn.first().boundsInRoot.top < firstColumn.last().boundsInRoot.top)
    }

    @Test
    fun theFormulaAndTheNoteMarkerSitBetweenTheTextsAroundThem() {
        show()
        val ordered =
            (nodes(SemanticsProperties.Text) + nodes(SemanticsProperties.ContentDescription))
                .distinct()
                .filter { it.config.getOrNull(SemanticsProperties.TraversalIndex) != null }
                .sortedBy { it.config[SemanticsProperties.TraversalIndex] }
        val labels =
            ordered.map {
                textOf(
                    it,
                ).ifEmpty {
                    it.config
                        .getOrNull(SemanticsProperties.ContentDescription)
                        ?.joinToString()
                        .orEmpty()
                }
            }
        val opening = labels.indexOfFirst { it.startsWith("Opening Heading") }
        val claim = labels.indexOfFirst { it.startsWith("A claim worth a note") }
        val note = labels.indexOfFirst { it == "Footnote n" }
        val second = labels.indexOfFirst { it.startsWith("Second Heading") }
        val formula = labels.indexOfFirst { it.contains("E = mc^2") }
        assertTrue("$labels", opening < claim && claim < note && note < second && second < formula)
    }

    @Test
    fun imagesAreImageNodesWithTheirAltTextOrAPlainFallback() {
        show("![A red dot on white](dot.png)\n\n![](plain.png)\n\n$markdown")
        val images =
            nodes(SemanticsProperties.Role).filter { it.config[SemanticsProperties.Role] == Role.Image }
        val labels = images.map { it.config[SemanticsProperties.ContentDescription].joinToString() }
        assertEquals(listOf("A red dot on white", "Image"), labels.sorted())
    }

    @Test
    fun aFencedCodeBlockAnnouncesItsLanguageOnceAndPlainCodeDoesNot() {
        show("```kotlin\nval x = 1\n```\n\n```\nplain\n```\n\n$markdown")
        val code = nodes(SemanticsProperties.Text).filter { textOf(it).startsWith("val x") || textOf(it) == "plain" }
        assertEquals(2, code.size)
        val states = code.associate { textOf(it) to it.config.getOrNull(SemanticsProperties.StateDescription) }
        assertEquals("kotlin code", states["val x = 1"])
        assertEquals(null, states["plain"])
    }
}
