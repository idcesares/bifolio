package dev.dcesares.bifolio

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

private const val PARAGRAPHS = 400
private const val TURNS = 5
private const val MAX_BLOCK_DRIFT = 1

/**
 * ANC-01 to ANC-07 on a real Activity: the reading anchor survives rotation, recreation, a cold reopen
 * (what process death restores from) and, on a foldable image, fold and unfold. The anchor is read back from
 * the store the app itself writes on stop, so the test needs no hooks in the app.
 */
class AnchorSurvivalTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as BifolioApp
    private lateinit var uri: Uri

    @Before
    fun seedDocument() {
        val file = File(context.cacheDir, "anchor-${System.nanoTime()}.md")
        file.writeText(
            buildString {
                append("# Anchor test\n\n")
                repeat(
                    PARAGRAPHS,
                ) { append("Paragraph $it. ${"The quick brown fox jumps over the lazy dog. ".repeat(6)}\n\n") }
            },
        )
        uri = Uri.fromFile(file)
        runBlocking { app.readingPreferences.setMode(ReadingMode.PAGED) }
    }

    private fun launch(): ActivityScenario<MainActivity> {
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, "text/markdown")
        return ActivityScenario.launch<MainActivity>(intent)
    }

    private fun turn(times: Int) {
        repeat(times) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
    }

    /** Stops and resumes the Activity so the app flushes its latest anchor, then reads it from the store. */
    private fun storedAnchor(scenario: ActivityScenario<MainActivity>): Anchor {
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        val anchor = runBlocking { app.recents.anchorFor(uri.toString()) }
        assertNotNull("no anchor stored", anchor)
        return anchor!!
    }

    private fun assertSamePlace(
        expected: Anchor,
        actual: Anchor,
    ) {
        assertTrue(
            "anchor moved from $expected to $actual",
            abs(expected.blockIndex - actual.blockIndex) <= MAX_BLOCK_DRIFT,
        )
    }

    private fun shell(command: String): String {
        val fd = instrumentation.uiAutomation.executeShellCommand(command)
        return java.io
            .FileInputStream(fd.fileDescriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    @Test
    fun anchorSurvivesRotation() {
        launch().use { scenario ->
            turn(TURNS)
            val before = storedAnchor(scenario)
            val automation = instrumentation.uiAutomation
            try {
                automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
                compose.waitForIdle()
                assertSamePlace(before, storedAnchor(scenario))
                automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                compose.waitForIdle()
                assertSamePlace(before, storedAnchor(scenario))
            } finally {
                automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            }
        }
    }

    @Test
    fun anchorSurvivesRecreation() {
        launch().use { scenario ->
            turn(TURNS)
            val before = storedAnchor(scenario)
            scenario.recreate()
            compose.waitForIdle()
            assertSamePlace(before, storedAnchor(scenario))
        }
    }

    /** A cold reopen takes the same path as process death: the view model is empty and the anchor comes from storage. */
    @Test
    fun anchorSurvivesColdReopen() {
        val before =
            launch().use { scenario ->
                turn(TURNS)
                storedAnchor(scenario)
            }
        launch().use { scenario ->
            compose.waitForIdle()
            assertSamePlace(before, storedAnchor(scenario))
        }
    }

    @Test
    fun anchorSurvivesFoldAndUnfoldTwentyTimes() {
        val states =
            Regex(
                """identifier=(\d+), name='(\w+)'""",
            ).findAll(shell("cmd device_state print-states")).toList()
        val closed = states.firstOrNull { it.groupValues[2].contains("CLOSED") }?.groupValues?.get(1)
        val opened = states.firstOrNull { it.groupValues[2] == "OPENED" }?.groupValues?.get(1)
        assumeTrue("needs a foldable image with CLOSED and OPENED device states", closed != null && opened != null)
        launch().use { scenario ->
            try {
                shell("cmd device_state state $opened")
                compose.waitForIdle()
                turn(TURNS)
                val before = storedAnchor(scenario)
                repeat(FOLD_CYCLES) {
                    shell("cmd device_state state $closed")
                    compose.waitForIdle()
                    shell("cmd device_state state $opened")
                    compose.waitForIdle()
                }
                assertSamePlace(before, storedAnchor(scenario))
            } finally {
                shell("cmd device_state state reset")
            }
        }
    }

    private companion object {
        const val FOLD_CYCLES = 20
    }
}
