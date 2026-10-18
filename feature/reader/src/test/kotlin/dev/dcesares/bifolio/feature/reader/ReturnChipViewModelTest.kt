package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** ANC-06: the return chip remembers the pre-jump anchor, expires after 8 s, and sends the reader back. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnChipViewModelTest {
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
    fun `no chip until a jump`() {
        assertNull(viewModel().returnAnchor.value)
    }

    @Test
    fun `a jump offers the anchor the reader was at`() {
        val vm = viewModel()
        vm.onAnchorSettled(Anchor(1, 5))
        vm.onJump()
        vm.onAnchorSettled(Anchor(9, 0))
        assertEquals(Anchor(1, 5), vm.returnAnchor.value)
    }

    @Test
    fun `the chip leaves after 8 seconds`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = viewModel()
            testScheduler.advanceUntilIdle()
            vm.onAnchorSettled(Anchor(1, 5))
            vm.onJump()
            advanceTimeBy(RETURN_CHIP_MILLIS - 1)
            assertEquals(Anchor(1, 5), vm.returnAnchor.value)
            advanceTimeBy(2)
            assertNull(vm.returnAnchor.value)
        }

    @Test
    fun `a second jump replaces the first and restarts the clock`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val vm = viewModel()
            testScheduler.advanceUntilIdle()
            vm.onAnchorSettled(Anchor(1, 0))
            vm.onJump()
            advanceTimeBy(RETURN_CHIP_MILLIS - 1_000)
            vm.onAnchorSettled(Anchor(4, 0))
            vm.onJump()
            advanceTimeBy(RETURN_CHIP_MILLIS - 1_000)
            assertEquals(Anchor(4, 0), vm.returnAnchor.value)
        }

    @Test
    fun `returning sends the pre-jump anchor to the surface and clears the chip`() =
        runTest {
            val vm = viewModel()
            val received = mutableListOf<Anchor>()
            val job = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch { vm.jumps.toList(received) }
            vm.onAnchorSettled(Anchor(1, 5))
            vm.onJump()
            vm.onAnchorSettled(Anchor(9, 0))
            vm.returnToJump()
            assertEquals(listOf(Anchor(1, 5)), received)
            assertNull(vm.returnAnchor.value)
            vm.returnToJump()
            assertEquals(1, received.size)
            job.cancel()
        }

    @Test
    fun `opening another document drops the chip`() {
        val vm = viewModel()
        vm.onJump()
        vm.open(OpenRequest("doc", "text/markdown", persistGrant = false))
        assertNull(vm.returnAnchor.value)
    }
}
