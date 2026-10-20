package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.SearchMatch
import dev.dcesares.bifolio.layout.SearchResult
import dev.dcesares.bifolio.layout.SearchSnippet
import dev.dcesares.bifolio.model.Anchor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI-08: result navigation wraps, a blank query clears, new results replace old ones, back closes one level. */
class SearchStateTest {
    private fun match(i: Int) = SearchMatch(Anchor(i, 0), i, 3, 3, SearchSnippet("abc", 0, 3))

    private fun result(
        count: Int,
        truncated: Boolean = false,
    ) = SearchResult(List(count) { match(it) }, truncated)

    private fun answered(count: Int) = SearchState().opened().withQuery("abc").withResult(result(count))

    @Test
    fun `results arrive with no current one so typing never moves the page`() {
        val state = answered(3)
        assertEquals(NO_RESULT, state.current)
        assertNull(state.currentMatch)
        assertEquals(SearchPhase.Done, state.phase)
    }

    @Test
    fun `next from no current is the first and wraps from the last to the first`() {
        var state = answered(3).next()
        assertEquals(0, state.current)
        state = state.next().next()
        assertEquals(2, state.current)
        assertEquals(0, state.next().current)
    }

    @Test
    fun `previous from no current is the last and wraps from the first to the last`() {
        var state = answered(3).previous()
        assertEquals(2, state.current)
        state = state.previous().previous()
        assertEquals(0, state.current)
        assertEquals(2, state.previous().current)
    }

    @Test
    fun `a single result wraps onto itself`() {
        val state = answered(1).next()
        assertEquals(0, state.next().current)
        assertEquals(0, state.previous().current)
    }

    @Test
    fun `stepping with no results changes nothing`() {
        val state = answered(0)
        assertEquals(state, state.next())
        assertEquals(state, state.previous())
    }

    @Test
    fun `a blank query clears the results at once and is idle`() {
        val state = answered(3).next().withQuery("   ")
        assertEquals(SearchPhase.Idle, state.phase)
        assertEquals(0, state.count)
        assertEquals(NO_RESULT, state.current)
        assertFalse(state.showsHighlights)
    }

    @Test
    fun `a non blank query keeps the old results until the new ones arrive`() {
        val state = answered(3).next().withQuery("abcd")
        assertEquals(SearchPhase.Searching, state.phase)
        assertEquals(3, state.count)
    }

    @Test
    fun `new results replace the old ones and reset the current`() {
        val state =
            answered(3)
                .next()
                .next()
                .withQuery("x")
                .withResult(result(2, truncated = true))
        assertEquals(2, state.count)
        assertEquals(NO_RESULT, state.current)
        assertTrue(state.result.truncated)
    }

    @Test
    fun `choosing a result on a compact window leaves the bar and on a wide one keeps the pane`() {
        assertEquals(SearchPanel.Bar, answered(3).selected(1, collapseList = true).panel)
        assertEquals(SearchPanel.List, answered(3).selected(1, collapseList = false).panel)
        assertEquals(1, answered(3).selected(1, collapseList = true).current)
    }

    @Test
    fun `choosing a result that does not exist changes nothing`() {
        val state = answered(2)
        assertEquals(state, state.selected(2, collapseList = true))
        assertEquals(state, state.selected(-1, collapseList = true))
    }

    @Test
    fun `back closes one level on a compact window and everything on a wide one`() {
        val chosen = answered(3).selected(1, collapseList = false)
        val bar = chosen.backed(hasBar = true)
        assertEquals(SearchPanel.Bar, bar.panel)
        assertEquals(SearchPanel.Closed, bar.backed(hasBar = true).panel)
        assertEquals(SearchPanel.Closed, chosen.backed(hasBar = false).panel)
    }

    @Test
    fun `back from a list with no chosen result closes search`() {
        assertEquals(SearchPanel.Closed, answered(3).backed(hasBar = true).panel)
    }

    @Test
    fun `closing forgets the session so no highlight survives`() {
        val state = answered(3).next().closed()
        assertEquals(SearchState(), state)
        assertFalse(state.showsHighlights)
    }

    @Test
    fun `highlights show only while search is open and has matches`() {
        assertTrue(answered(2).showsHighlights)
        assertFalse(answered(0).showsHighlights)
        assertFalse(answered(2).copy(panel = SearchPanel.Closed).showsHighlights)
    }

    @Test
    fun `the list button brings the list back over the bar`() {
        val bar = answered(3).selected(0, collapseList = true)
        assertEquals(SearchPanel.List, bar.listShown().panel)
        assertEquals(SearchPanel.Closed, SearchState().listShown().panel)
    }
}
