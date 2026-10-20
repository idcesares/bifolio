package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.SearchMatch
import dev.dcesares.bifolio.layout.SearchResult

/** What of the search is on screen: nothing, the result list (sheet or pane), or the compact bar over the page. */
internal enum class SearchPanel { Closed, List, Bar }

/** Where the current query stands: blank, waiting for the debounce or the engine, or answered. */
internal enum class SearchPhase { Idle, Searching, Done }

/** The index of "no current result". */
internal const val NO_RESULT = -1

private val NoMatches = SearchResult(emptyList(), false)

/**
 * The search session as plain data (UI-08). Every change is a pure function so the wrap-around, the blank query and
 * the replaced results are tested on the JVM. [current] is an index into the matches or [NO_RESULT]: results that
 * just arrived have no current one, so typing never moves the page by itself.
 */
internal data class SearchState(
    val panel: SearchPanel = SearchPanel.Closed,
    val query: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val result: SearchResult = NoMatches,
    val current: Int = NO_RESULT,
) {
    val matches: List<SearchMatch> get() = result.matches
    val count: Int get() = result.matches.size
    val currentMatch: SearchMatch? get() = result.matches.getOrNull(current)

    /** Highlights are drawn while a search is on screen and has something to show. */
    val showsHighlights: Boolean get() = panel != SearchPanel.Closed && count > 0

    /** Opens the result list. */
    fun opened(): SearchState = copy(panel = SearchPanel.List)

    /** The query changed. A blank one clears at once; any other waits for its results. */
    fun withQuery(text: String): SearchState =
        if (text.isBlank()) {
            copy(query = text, phase = SearchPhase.Idle, result = NoMatches, current = NO_RESULT)
        } else {
            copy(query = text, phase = SearchPhase.Searching)
        }

    /** Results for the query arrived; they replace the old ones and nothing is current. */
    fun withResult(found: SearchResult): SearchState =
        copy(phase = SearchPhase.Done, result = found, current = NO_RESULT)

    /** Next result, wrapping from the last to the first. From no current result it is the first. */
    fun next(): SearchState = stepTo(if (current == NO_RESULT) 0 else (current + 1) % count)

    /** Previous result, wrapping from the first to the last. From no current result it is the last. */
    fun previous(): SearchState = stepTo(if (current == NO_RESULT) count - 1 else (current - 1 + count) % count)

    private fun stepTo(index: Int): SearchState = if (count == 0) this else copy(current = index)

    /** A result was chosen in the list; on a compact window the list gives way to the bar, on a wide one it stays. */
    fun selected(
        index: Int,
        collapseList: Boolean,
    ): SearchState =
        if (index !in 0 until count) {
            this
        } else {
            copy(current = index, panel = if (collapseList) SearchPanel.Bar else panel)
        }

    /** The bar's list button. */
    fun listShown(): SearchState = if (panel == SearchPanel.Closed) this else copy(panel = SearchPanel.List)

    /**
     * Back or Escape closes one level: on a compact window the list over a chosen result gives way to the bar
     * first ([hasBar]); a wide window has the pane only, so it closes search.
     */
    fun backed(hasBar: Boolean): SearchState =
        when {
            hasBar && panel == SearchPanel.List && current != NO_RESULT -> copy(panel = SearchPanel.Bar)
            else -> closed()
        }

    /** Closes search and forgets the session: highlights go with it. */
    fun closed(): SearchState = SearchState()
}

/** The wait after the last keystroke before the engine runs (UI-08). */
internal const val SEARCH_DEBOUNCE_MILLIS = 250L
