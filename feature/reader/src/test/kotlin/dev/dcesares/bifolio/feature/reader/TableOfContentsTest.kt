package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.layout.FoldOcclusion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI-07: the heading list, the current section by anchor, and the H4 to H6 collapse. */
class TableOfContentsTest {
    private fun toc(markdown: String) = MarkdownParser.parse(markdown).let { it to TableOfContents(it.blocks) }

    @Test
    fun noHeadingsMeansNoContents() {
        val (_, contents) = toc("just text\n\nmore text\n")
        assertTrue(contents.isEmpty)
        assertTrue(contents.rows(emptySet()).isEmpty())
    }

    @Test
    fun entriesFollowDocumentOrderWithPlainTextTitles() {
        val (_, contents) = toc("# One *two*\n\ntext\n\n## Three `code`\n\n### Four\n")
        assertEquals(listOf("One two", "Three code", "Four"), contents.entries.map { it.title })
        assertEquals(listOf(1, 2, 3), contents.entries.map { it.level })
        assertEquals(listOf("one-two", "three-code", "four"), contents.entries.map { it.slug })
    }

    @Test
    fun topLevelHeadingAnchorsAtItsBlockStart() {
        val (doc, contents) = toc("intro\n\n# One\n\ntext\n\n## Two\n")
        assertEquals(Anchor(1, 0), contents.entries[0].anchor)
        assertEquals(Anchor(3, 0), contents.entries[1].anchor)
        assertEquals(doc.blocks[1].range.start, doc.sourceOffsetOf(contents.entries[0].anchor))
    }

    @Test
    fun nestedHeadingsKeepTheirSourcePositionInsideTheContainer() {
        val (doc, contents) = toc("> # First\n>\n> text\n>\n> ## Second\n\n# After\n")
        val first = contents.entries[0].anchor
        val second = contents.entries[1].anchor
        assertEquals(0, first.blockIndex)
        assertEquals(0, second.blockIndex)
        assertTrue(second.charOffset > first.charOffset)
        assertEquals("# First", doc.source.substring(doc.sourceOffsetOf(first)).take("# First".length))
        assertEquals("## Second", doc.source.substring(doc.sourceOffsetOf(second)).take("## Second".length))
        assertEquals(Anchor(1, 0), contents.entries[2].anchor)
    }

    @Test
    fun headingsWithoutVisibleTextAreLeftOut() {
        val (_, contents) = toc("#\n\n# Real\n")
        assertEquals(listOf("Real"), contents.entries.map { it.title })
    }

    @Test
    fun currentSectionIsTheLastHeadingAtOrBeforeTheAnchor() {
        val (_, contents) = toc("intro\n\n# One\n\ntext\n\n## Two\n\ntext\n\n## Three\n")
        assertNull(contents.currentIndex(Anchor(0, 5)))
        assertEquals(0, contents.currentIndex(Anchor(1, 0)))
        assertEquals(0, contents.currentIndex(Anchor(2, 3)))
        assertEquals(1, contents.currentIndex(Anchor(3, 0)))
        assertEquals(1, contents.currentIndex(Anchor(4, 0)))
        assertEquals(2, contents.currentIndex(Anchor(5, 0)))
        assertEquals(2, contents.currentIndex(Anchor(99, 0)))
    }

    @Test
    fun currentSectionSeesHeadingsInsideOneContainerByOffset() {
        val (_, contents) = toc("> # First\n>\n> text\n>\n> ## Second\n")
        val second = contents.entries[1].anchor
        assertEquals(0, contents.currentIndex(Anchor(0, second.charOffset - 1)))
        assertEquals(1, contents.currentIndex(second))
    }

    @Test
    fun headingsDeeperThanThreeAreCollapsedUnderTheirParent() {
        val (_, contents) = toc("# A\n\n## B\n\n### C\n\n#### D\n\n##### E\n\n## F\n")
        assertEquals(listOf("A", "B", "C", "F"), contents.rows(emptySet()).map { it.entry.title })
        val c = contents.rows(emptySet()).first { it.entry.title == "C" }
        assertEquals(2, c.deeper)
        assertFalse(c.expanded)
        val open = contents.rows(setOf(c.index))
        assertEquals(listOf("A", "B", "C", "D", "E", "F"), open.map { it.entry.title })
        assertTrue(open.first { it.entry.title == "C" }.expanded)
    }

    @Test
    fun rowsAreIndentedRelativeToTheShallowestLevel() {
        val (_, contents) = toc("## A\n\n### B\n\n#### C\n")
        val rows = contents.rows(setOf(1))
        assertEquals(listOf(0, 1, 2), rows.map { it.depth })
    }

    @Test
    fun deepHeadingsWithoutAShallowParentStayReachable() {
        val (_, contents) = toc("#### Lone\n\n##### Child\n\n# Real\n\n#### Under real\n")
        assertEquals(listOf("Lone", "Child", "Real"), contents.rows(emptySet()).map { it.entry.title })
        assertEquals(2, contents.rows(emptySet()).last().index)
        assertEquals(0, contents.ownerOf(0))
        assertEquals(1, contents.ownerOf(1))
        assertEquals(2, contents.ownerOf(3))
    }

    @Test
    fun aCollapsedCurrentSectionHighlightsItsVisibleParent() {
        val (_, contents) = toc("# A\n\n#### Deep\n\ntext\n\n# B\n")
        val deep = contents.entries.indexOfFirst { it.title == "Deep" }
        val current = contents.currentIndex(contents.entries[deep].anchor)
        assertEquals(deep, current)
        assertEquals(0, contents.highlightedRow(current, emptySet()))
        assertEquals(deep, contents.highlightedRow(current, setOf(0)))
        assertNull(contents.highlightedRow(null, emptySet()))
    }

    private fun fold(
        orientation: FoldOrientation,
        separating: Boolean,
    ) = FoldSpec(100, 200, 120, 240, separating, orientation, true, FoldOcclusion.None)

    @Test
    fun onlySeparatingFoldsClearThePane() {
        val px = { value: Int -> value.dp }
        assertEquals(TocClearance(), null.tocClearance(px))
        assertEquals(TocClearance(), fold(FoldOrientation.Vertical, false).tocClearance(px))
        assertEquals(TocClearance(start = 120.dp), fold(FoldOrientation.Vertical, true).tocClearance(px))
        assertEquals(TocClearance(top = 240.dp), fold(FoldOrientation.Horizontal, true).tocClearance(px))
    }

    @Test
    fun theParentOfTheCurrentSectionStartsExpanded() {
        val (_, contents) = toc("# A\n\n#### Deep\n\n# B\n")
        assertEquals(setOf(0), contents.initiallyExpanded(1))
        assertEquals(emptySet<Int>(), contents.initiallyExpanded(0))
        assertEquals(emptySet<Int>(), contents.initiallyExpanded(null))
    }
}
