package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** NAV-01: a horizontal swipe turns the spread, following the finger and its velocity. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class PageTurnControllerTest {
    private fun controller(
        scope: TestScope,
        turns: MutableList<Int>,
    ) = PageTurnController(CoroutineScope(scope.coroutineContext + TestMonotonicFrameClock(scope))).apply {
        width = 1000f
        canTurn = { true }
        commit = { turns += it }
    }

    @Test
    fun `a slow drag past a third of the page turns forward`() =
        runTest {
            val turns = mutableListOf<Int>()
            val c = controller(this, turns)
            c.drag(-400f)
            assertEquals(-0.4f, c.progress, 0.001f)
            c.release(velocity = -100f, totalDx = -400f)
            advanceUntilIdle()
            assertEquals(listOf(-1), turns)
        }

    @Test
    fun `a short drag springs back`() =
        runTest {
            val turns = mutableListOf<Int>()
            val c = controller(this, turns)
            c.drag(-100f)
            c.release(velocity = -100f, totalDx = -100f)
            advanceUntilIdle()
            assertEquals(emptyList<Int>(), turns)
            assertEquals(0f, c.progress, 0f)
        }

    @Test
    fun `a fast flick turns even from a short drag`() =
        runTest {
            val turns = mutableListOf<Int>()
            val c = controller(this, turns)
            c.drag(100f)
            c.release(velocity = 3000f, totalDx = 100f)
            advanceUntilIdle()
            assertEquals(listOf(1), turns)
        }

    @Test
    fun `no turn past the first or last spread`() =
        runTest {
            val turns = mutableListOf<Int>()
            val c = controller(this, turns).apply { canTurn = { false } }
            c.drag(-600f)
            c.release(velocity = -3000f, totalDx = -600f)
            advanceUntilIdle()
            assertEquals(emptyList<Int>(), turns)
        }

    @Test
    fun `a turn against the end of the document reports the edge once`() =
        runTest {
            val turns = mutableListOf<Int>()
            val edges = mutableListOf<Int>()
            val c = controller(this, turns)
            c.canTurn = { false }
            c.onEdge = { edges += it }
            c.turn(-1)
            c.drag(-400f)
            c.release(velocity = -100f, totalDx = -400f)
            advanceUntilIdle()
            assertEquals(listOf(-1, -1), edges)
            assertEquals(emptyList<Int>(), turns)
        }
}
