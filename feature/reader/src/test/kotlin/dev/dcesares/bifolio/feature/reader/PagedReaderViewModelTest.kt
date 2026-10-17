package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.PageFlow
import dev.dcesares.bifolio.layout.Paginator
import dev.dcesares.bifolio.layout.compareReadingOrder
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val DOC =
    (0 until 240).joinToString("\n\n") { i -> "Paragraph number $i " + "lorem ipsum dolor sit amet ".repeat(12) }

private val PHONE =
    PageRequest(widthPx = 400, heightPx = 640, density = 1f, fontScale = 1f, bodySp = 18f, languageTag = "en")
private val TABLET =
    PageRequest(widthPx = 1280, heightPx = 800, density = 1f, fontScale = 1f, bodySp = 18f, languageTag = "en")

/** SCO-03 (mode), ANC-01 to ANC-04 (anchor survives mode and geometry changes), PAG-09 (stages), DOC-05. */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedReaderViewModelTest {
    private val repo = RecentsRepository(InMemoryPreferences(), RecordingGrants())
    private val prefs = ReadingPreferences(InMemoryPreferences())
    private val engine = GridTextEngine()
    private val stages = mutableListOf<String>()

    @BeforeEach
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun opened(
        text: String = DOC,
        declaredSize: Long? = null,
    ): ReaderViewModel {
        val vm =
            ReaderViewModel(
                reader = FakeReader(mapOf("doc" to raw(text, declaredSize = declaredSize))),
                repository = repo,
                preferences = prefs,
                engine = engine,
                computeDispatcher = Dispatchers.Main,
                perf = { name, _ -> stages.add(name) },
            )
        vm.open(OpenRequest("doc", "text/markdown", persistGrant = false))
        return vm
    }

    private fun ReaderViewModel.layoutCoversAnchor() {
        val layout = paged.value
        assertNotNull(layout)
        val flow = layout!!.flow
        val at = anchor.value
        val spread = flow.spreadIndexOf(at)
        assertTrue(spread >= 0, "no spread for $at")
        val pages = flow.pagesOfSpread(spread)
        val first = pages.first()
        assertTrue(first.anchor.compareReadingOrder(at) <= 0, "spread starts after the anchor: ${first.anchor} > $at")
        val after = flow.pages.getOrNull(spread * flow.columnCount + pages.size)
        assertTrue(after == null || after.anchor.compareReadingOrder(at) > 0, "anchor is past the spread")
    }

    @Test
    fun `paged is the default mode and the center switch persists the choice globally`() =
        runTest {
            val vm = opened()
            assertEquals(ReadingMode.PAGED, vm.mode.value)
            vm.toggleMode()
            assertEquals(ReadingMode.SCROLL, vm.mode.value)
            val second =
                ReaderViewModel(FakeReader(emptyMap()), repo, prefs, engine, computeDispatcher = Dispatchers.Main)
            assertEquals(ReadingMode.SCROLL, second.mode.value, "a new view model reads the same global profile")
        }

    @Test
    fun `switching mode back and forth keeps the anchor`() =
        runTest {
            val vm = opened()
            val at = Anchor(130, 42)
            vm.onAnchorSettled(at)
            repeat(5) {
                vm.toggleMode()
                assertEquals(at, vm.anchor.value)
                vm.toggleMode()
                assertEquals(at, vm.anchor.value)
            }
            assertEquals(ReadingMode.PAGED, vm.mode.value)
        }

    @Test
    fun `rotating ten times keeps the anchor and always shows a page that covers it`() =
        runTest {
            val vm = opened()
            val at = Anchor(150, 77)
            vm.onAnchorSettled(at)
            repeat(10) {
                vm.paginate(PHONE)
                assertEquals(at, vm.anchor.value)
                vm.layoutCoversAnchor()
                vm.paginate(TABLET)
                assertEquals(at, vm.anchor.value)
                vm.layoutCoversAnchor()
            }
        }

    @Test
    fun `every pagination stage reaches the view model in order and leaves the anchor alone`() =
        runTest {
            val vm = opened()
            val at = Anchor(200, 5)
            vm.onAnchorSettled(at)
            vm.paginate(PHONE)
            assertEquals(listOf("pagination.provisional", "pagination.canonical", "pagination.complete"), stages)
            assertEquals(PaginationStage.COMPLETE, vm.paged.value?.stage)
            assertEquals(at, vm.anchor.value, "layout stages never move the anchor")
        }

    @Test
    fun `provisional canonical and complete snapshots each show a spread that covers the anchor`() {
        val ready = (opened().state.value as ReaderUiState.Ready)
        val text = engine.prepare(PHONE)
        val geometry = GeometryCalculator.compute(text.spec)
        val at = Anchor(200, 5)
        val seen = mutableListOf<PageFlow>()
        Paginator(ready.document, text.measurer, geometry).paginateAround(at) { seen.add(it) }
        assertEquals(3, seen.size)
        for (flow in seen) {
            val first = flow.pagesOfSpread(flow.spreadIndexOf(at)).first()
            assertTrue(first.anchor.compareReadingOrder(at) <= 0, "spread starts after the anchor")
        }
    }

    @Test
    fun `an identical request is ignored and a new one replaces the layout`() =
        runTest {
            val vm = opened()
            vm.paginate(PHONE)
            vm.paginate(PHONE)
            assertEquals(1, engine.prepared)
            vm.paginate(TABLET)
            assertEquals(2, engine.prepared)
            assertEquals(TABLET, vm.paged.value?.request)
            assertTrue(
                vm.paged.value!!
                    .geometry.columnCount > 1,
                "the wide window gets more than one column",
            )
        }

    @Test
    fun `files too large for pages scroll whatever the preference says and never paginate`() =
        runTest {
            val vm = opened(declaredSize = 9L * 1024 * 1024)
            assertEquals(ReadingMode.SCROLL, vm.mode.value)
            vm.paginate(PHONE)
            assertNull(vm.paged.value)
            vm.toggleMode()
            assertEquals(ReadingMode.SCROLL, vm.mode.value)
        }

    @Test
    fun `settling stores the anchor, not a page number`() =
        runTest {
            val vm = opened()
            vm.paginate(PHONE)
            val page =
                vm.paged.value!!
                    .flow.pages[7]
            vm.onAnchorSettled(page.anchor)
            vm.flush()
            assertEquals(page.anchor, repo.anchorFor("doc"))
        }
}
