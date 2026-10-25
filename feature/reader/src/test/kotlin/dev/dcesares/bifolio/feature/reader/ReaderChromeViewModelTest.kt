package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val HIDE = CHROME_AUTO_HIDE_MILLIS

/** UI-03 (center tap shows the chrome, 8 s auto-hide), UI-04 (bars follow it), NAV-02 (a tap never changes mode). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderChromeViewModelTest {
    private val repo = RecentsRepository(InMemoryPreferences(), RecordingGrants())
    private val prefs = ReadingPreferences(InMemoryPreferences())

    @BeforeEach
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(): ReaderViewModel =
        ReaderViewModel(
            reader = FakeReader(mapOf("doc" to raw("# Title\n\nBody."))),
            repository = repo,
            preferences = prefs,
            engine = GridTextEngine(),
            computeDispatcher = Dispatchers.Main,
            chromeDispatcher = Dispatchers.Main,
        ).also { it.open(OpenRequest("doc", "text/markdown", persistGrant = false)) }

    @Test
    fun `the reader opens immersive`() =
        runTest {
            assertFalse(viewModel().chromeVisible.value)
        }

    @Test
    fun `a center tap shows the chrome and the next one hides it`() =
        runTest {
            val vm = viewModel()
            vm.toggleChrome()
            assertTrue(vm.chromeVisible.value)
            vm.toggleChrome()
            assertFalse(vm.chromeVisible.value)
        }

    @Test
    fun `toggling the chrome never changes the reading mode`() =
        runTest {
            val vm = viewModel()
            val before = vm.mode.value
            repeat(5) { vm.toggleChrome() }
            assertEquals(before, vm.mode.value)
            assertEquals(ReadingMode.PAGED, before)
            assertEquals(ReadingMode.PAGED, ReaderViewModel(FakeReader(emptyMap()), repo, prefs).mode.value)
        }

    @Test
    fun `the chrome hides itself after four seconds`() =
        runTest {
            val vm = viewModel()
            vm.toggleChrome()
            advanceTimeBy(HIDE - 1)
            assertTrue(vm.chromeVisible.value, "still shown just before 8 s")
            advanceTimeBy(2)
            assertFalse(vm.chromeVisible.value)
        }

    @Test
    fun `using the chrome restarts the countdown`() =
        runTest {
            val vm = viewModel()
            vm.toggleChrome()
            advanceTimeBy(HIDE - 500)
            vm.toggleMode()
            advanceTimeBy(HIDE - 500)
            assertTrue(vm.chromeVisible.value, "the mode control restarted the 8 s")
            advanceTimeBy(1_000)
            assertFalse(vm.chromeVisible.value)
        }

    @Test
    fun `hiding by tap cancels the countdown so a later show gets a full four seconds`() =
        runTest {
            val vm = viewModel()
            vm.toggleChrome()
            advanceTimeBy(HIDE - 1_000)
            vm.toggleChrome()
            advanceTimeBy(500)
            vm.toggleChrome()
            advanceTimeBy(HIDE - 1)
            assertTrue(vm.chromeVisible.value)
        }

    @Test
    fun `closing the document leaves the chrome hidden`() =
        runTest {
            val vm = viewModel()
            vm.toggleChrome()
            vm.close()
            assertFalse(vm.chromeVisible.value)
        }
}
