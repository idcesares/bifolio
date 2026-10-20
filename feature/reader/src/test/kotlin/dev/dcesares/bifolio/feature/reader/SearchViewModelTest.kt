package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.layout.DocumentSearch
import dev.dcesares.bifolio.layout.SearchResult
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** UI-08, ANC-06: debounced search on the compute dispatcher, cancellation of the previous query, one return chip. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val repo = RecentsRepository(InMemoryPreferences(), RecordingGrants())
    private val prefs = ReadingPreferences(InMemoryPreferences())
    private val text = "# Title\n\nalpha beta\n\nbeta gamma\n\nBeta again"

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        searcher: ((dev.dcesares.bifolio.model.Document, String, () -> Unit) -> SearchResult)? = null,
    ): ReaderViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm =
            ReaderViewModel(
                reader = FakeReader(mapOf("doc" to raw(text))),
                repository = repo,
                preferences = prefs,
                engine = GridTextEngine(),
                computeDispatcher = Dispatchers.Main,
                chromeDispatcher = Dispatchers.Main,
                searcher = searcher ?: { d, q, c -> DocumentSearch.search(d, q, checkCancelled = c) },
            )
        vm.open(OpenRequest("doc", "text/markdown", persistGrant = false))
        testScheduler.advanceUntilIdle()
        return vm
    }

    @Test
    fun `typing waits for the debounce and runs the engine once for the last query`() =
        runTest {
            val queries = mutableListOf<String>()
            val vm =
                viewModel { d, q, c ->
                    queries += q
                    DocumentSearch.search(d, q, checkCancelled = c)
                }
            vm.openSearch()
            vm.setSearchQuery("b")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 50)
            vm.setSearchQuery("be")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 50)
            vm.setSearchQuery("beta")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
            assertEquals(SearchPhase.Searching, vm.search.value.phase)
            assertTrue(queries.isEmpty())
            advanceTimeBy(2)
            testScheduler.runCurrent()
            assertEquals(listOf("beta"), queries)
            assertEquals(SearchPhase.Done, vm.search.value.phase)
            assertEquals(3, vm.search.value.count)
        }

    @Test
    fun `a blank query clears at once without running the engine`() =
        runTest {
            var runs = 0
            val vm =
                viewModel { d, q, c ->
                    runs++
                    DocumentSearch.search(d, q, checkCancelled = c)
                }
            vm.openSearch()
            vm.setSearchQuery("beta")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            testScheduler.runCurrent()
            assertEquals(1, runs)
            vm.setSearchQuery("")
            assertEquals(0, vm.search.value.count)
            assertEquals(SearchPhase.Idle, vm.search.value.phase)
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS * 4)
            assertEquals(1, runs)
        }

    @Test
    fun `a newer query cancels the one the engine is running and only the newer results are published`() =
        runTest {
            lateinit var vm: ReaderViewModel
            val published = mutableListOf<String>()
            vm =
                viewModel { d, q, c ->
                    if (q == "alpha") {
                        // The user types again while the engine works on "alpha": its next check must stop it.
                        vm.setSearchQuery("gamma")
                        c()
                    }
                    published += q
                    DocumentSearch.search(d, q, checkCancelled = c)
                }
            vm.openSearch()
            vm.setSearchQuery("alpha")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            testScheduler.runCurrent()
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            testScheduler.runCurrent()
            assertEquals(listOf("gamma"), published)
            assertEquals("gamma", vm.search.value.query)
            assertEquals(1, vm.search.value.count)
        }

    @Test
    fun `choosing results jumps to their anchors and the chip keeps the position the search began at`() =
        runTest {
            val vm = viewModel()
            val received = mutableListOf<Anchor>()
            val job = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch { vm.jumps.toList(received) }
            vm.onAnchorSettled(Anchor(0, 3))
            vm.openSearch()
            vm.setSearchQuery("beta")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            testScheduler.runCurrent()
            val matches = vm.search.value.matches
            assertEquals(3, matches.size)

            vm.selectSearchResult(0, collapseList = true)
            assertEquals(Anchor(0, 3), vm.returnAnchor.value)
            vm.onAnchorSettled(matches[0].anchor)
            vm.searchNext()
            vm.onAnchorSettled(matches[1].anchor)
            vm.searchPrevious()
            vm.searchPrevious()
            assertEquals(matches.map { it.anchor }.let { listOf(it[0], it[1], it[0], it[2]) }, received)
            assertEquals(Anchor(0, 3), vm.returnAnchor.value)

            vm.returnToJump()
            assertEquals(Anchor(0, 3), received.last())
            assertNull(vm.returnAnchor.value)
            job.cancel()
        }

    @Test
    fun `closing search or opening another document drops results and highlights`() =
        runTest {
            val vm = viewModel()
            vm.openSearch()
            vm.setSearchQuery("beta")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            testScheduler.runCurrent()
            assertTrue(vm.search.value.showsHighlights)
            vm.closeSearch()
            assertEquals(SearchState(), vm.search.value)

            vm.openSearch()
            vm.setSearchQuery("beta")
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
            testScheduler.runCurrent()
            vm.open(OpenRequest("doc", "text/markdown", persistGrant = false))
            assertEquals(SearchState(), vm.search.value)
        }

    @Test
    fun `closing while a search is pending stops it`() =
        runTest {
            var runs = 0
            val vm =
                viewModel { d, q, c ->
                    runs++
                    DocumentSearch.search(d, q, checkCancelled = c)
                }
            vm.openSearch()
            vm.setSearchQuery("beta")
            vm.closeSearch()
            advanceTimeBy(SEARCH_DEBOUNCE_MILLIS * 2)
            testScheduler.runCurrent()
            assertEquals(0, runs)
            assertEquals(SearchState(), vm.search.value)
        }

    @Test
    fun `search does not open before a document is ready`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = ReaderViewModel(FakeReader(emptyMap()), repo, prefs)
            vm.openSearch()
            assertEquals(SearchPanel.Closed, vm.search.value.panel)
        }
}
