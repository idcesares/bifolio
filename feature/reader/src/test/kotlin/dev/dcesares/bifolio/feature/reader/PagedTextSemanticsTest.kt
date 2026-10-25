package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
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

    private val settled = mutableListOf<Anchor>()

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
                    onSettled = { settled.add(it) },
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
        for (column in 0..1) {
            // A note marker button is padded to a touch target, so its bounds start above the line it stands in.
            val tops =
                ordered
                    .filterIndexed { i, node ->
                        columns[i] == column &&
                            node.config.getOrNull(SemanticsProperties.Role) != Role.Button
                    }.map { it.boundsInRoot.top }
            assertTrue(
                "column $column follows the page from top to bottom: $tops",
                tops.zipWithNext().all { (a, b) ->
                    a <=
                        b
                },
            )
        }
        // The second column starts at the top of the page, higher than the end of the first one.
        val firstColumn = ordered.filterIndexed { i, _ -> columns[i] == 0 }
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

    @Test
    fun tableCellsReachTalkBack() {
        show("| Alpha | Beta |\n|---|---|\n| Gamma | Delta |\n\n$markdown")
        val texts = nodes(SemanticsProperties.Text)
        val header = texts.first { textOf(it).startsWith("Alpha") }
        val body = texts.first { textOf(it).startsWith("Gamma") }
        // A row is one node, its cells joined by the separator from the resources.
        assertEquals("Alpha, Beta", textOf(header))
        assertEquals("Gamma, Delta", textOf(body))
        assertTrue("the header row is a heading", header.config.contains(SemanticsProperties.Heading))
        assertFalse("a body row is not", body.config.contains(SemanticsProperties.Heading))
        assertTrue(
            "the header row is read before the body row",
            header.config[SemanticsProperties.TraversalIndex] < body.config[SemanticsProperties.TraversalIndex],
        )
    }

    private fun group(): androidx.compose.ui.test.SemanticsNodeInteraction =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))

    private fun liveText(): String? =
        nodes(SemanticsProperties.LiveRegion)
            .singleOrNull { it.config[SemanticsProperties.LiveRegion] == LiveRegionMode.Polite }
            ?.config
            ?.get(SemanticsProperties.ContentDescription)
            ?.joinToString()

    private fun readFromHere() {
        val action = group().fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Read from here", action.label)
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    private fun scroll(forward: Boolean) {
        group().performSemanticsAction(SemanticsActions.ScrollBy) { it(if (forward) 1f else -1f, 0f) }
        compose.waitForIdle()
    }

    private fun firstTextOfSpread(): String =
        nodes(SemanticsProperties.Text)
            .filter { it.config.contains(SemanticsProperties.TraversalIndex) }
            .minBy { it.config[SemanticsProperties.TraversalIndex] }
            .let { textOf(it) }

    @Test
    fun readFromHereExposesTheWholeSpreadInReadingOrderOnce() {
        show()
        assertEquals("nothing is announced before the action", null, liveText())
        readFromHere()
        val speech = liveText().orEmpty()
        val lines = speech.lines()
        assertTrue(speech.take(200), lines.first().startsWith("Opening Heading"))
        val claim = lines.indexOfFirst { it.startsWith("A claim worth a note") }
        val second = lines.indexOfFirst { it.startsWith("Second Heading") }
        val formula = lines.indexOfFirst { it.contains("E = mc^2") }
        assertTrue("$lines", 0 < claim && claim < second && second < formula)
        assertFalse("the footnote label is not spoken inline", "Footnote" in lines[claim])
    }

    @Test
    fun scrollingForwardTurnsToTheNextSpreadByAnchorAndKeepsReading() {
        show()
        readFromHere()
        val before = liveText()
        scroll(forward = true)
        assertEquals("one anchor settled, the first character of the next spread", 1, settled.size)
        assertTrue("past the start of the document: ${settled[0]}", settled[0] != Anchor(0, 0))
        compose.waitUntil(WAIT_MILLIS) { liveText() != null && liveText() != before }
        assertFalse("the new spread starts elsewhere", firstTextOfSpread().startsWith("Opening Heading"))
        scroll(forward = false)
        compose.waitUntil(WAIT_MILLIS) { liveText() == before }
        assertEquals("back at the start", Anchor(0, 0), settled.last())
    }

    @Test
    fun aTurnByHandStopsTheReading() {
        show()
        readFromHere()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.waitUntil(WAIT_MILLIS) { liveText() == null }
    }

    @Test
    fun scrollingPastTheLastSpreadIsRefused() {
        show()
        val axis = group().fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        repeat(axis.maxValue().toInt()) {
            scroll(forward = true)
            compose.waitUntil(WAIT_MILLIS) { axis.value() >= 0f }
        }
        val last = settled.size
        scroll(forward = true)
        assertEquals("no turn beyond the end", last, settled.size)
    }
}
