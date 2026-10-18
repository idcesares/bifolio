package dev.dcesares.bifolio.feature.reader

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The clock starts an animation on its first frame, so a run lasts its duration plus up to two frames. */
private const val FRAME_SLACK = 40L
private const val MAX_FRAMES = 100
private const val TOLERANCE = 0.5f

/**
 * MOT-05, MOT-07, MOT-09, ANC-03: the anchor line glides from its old screen place to its new one; the anchor is
 * never changed by the motion, stays visible all the way, a second geometry change continues from where the line
 * is, and reduced motion keeps the highlight only. The animation clock is paused and advanced by hand.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ContinuityMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scope: CoroutineScope
    private lateinit var motion: ContinuityMotion

    private val document: Document =
        MarkdownParser.parse(
            List(60) { "Paragraph $it. " + "The quick brown fox jumps over the lazy dog. ".repeat(6) }
                .joinToString("\n\n"),
        )

    private fun request(
        width: Int,
        height: Int,
    ) = PageRequest(width, height, 2f, 1f, 18f, "en")

    private val phone = buildLayout(document, request(824, 1830))
    private val wide = buildLayout(document, request(1680, 1800))
    private val tablet = buildLayout(document, request(2560, 1800))

    private fun start() {
        compose.setContent {
            scope = rememberCoroutineScope()
            motion = androidx.compose.runtime.remember { ContinuityMotion(scope, Dispatchers.Unconfined) }
        }
        compose.mainClock.autoAdvance = false
        compose.waitForIdle()
    }

    /** Measures [shown] in [layout] on the motion and waits for the call to return. */
    private fun track(
        layout: PagedLayout,
        shown: Anchor,
        reduced: Boolean = false,
    ) {
        // Undispatched on the UI thread with an unconfined compute step: the call returns without moving the clock.
        compose.runOnIdle {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { motion.track(layout, shown, 0f, 0f, reduced) {} }
        }
    }

    private fun midAnchor(): Anchor = phone.flow.pages[phone.flow.pages.size / 2].anchor

    private fun displayedX() = checkNotNull(motion.spot).x + motion.offsetX

    private fun displayedY() = checkNotNull(motion.spot).y + motion.offsetY

    @Test
    fun anchorLineGlidesFromOldPlaceToNewAndStaysVisible() {
        start()
        val anchor = midAnchor()
        track(phone, anchor)
        val old = checkNotNull(motion.spot)
        assertEquals("no motion on the first layout", 0f, motion.glow, 0f)

        track(tablet, anchor)
        val new = checkNotNull(motion.spot)
        assertTrue("the new layout shows the anchor (ANC-03)", tablet.flow.spreadIndexOf(anchor) >= 0)
        val startTime = compose.mainClock.currentTime
        assertTrue(motion.gliding)
        assertEquals("starts where the line was", old.x, displayedX(), TOLERANCE)
        assertEquals("starts where the line was", old.y, displayedY(), TOLERANCE)

        var frames = 0
        while (motion.gliding && frames++ < MAX_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            assertTrue("anchor line visible horizontally", displayedX() in 0f..2560f)
            assertTrue("anchor line visible vertically", displayedY() in 0f..1800f)
        }
        assertTrue("the glide lasts about 250 ms", compose.mainClock.currentTime - startTime in 200L..400L)
        assertFalse(motion.gliding)
        assertEquals(new.x, displayedX(), TOLERANCE)
        assertEquals(new.y, displayedY(), TOLERANCE)
        assertTrue("highlight still fading after the glide (600 ms): ${motion.glow}", motion.glow > 0f)
        compose.mainClock.advanceTimeBy(BifolioMotion.ANCHOR_GLOW_DURATION_MILLIS + FRAME_SLACK)
        assertEquals(0f, motion.glow, 0f)
    }

    @Test
    fun secondGeometryChangeMidGlideContinuesFromWhereTheLineIs() {
        start()
        val anchor = midAnchor()
        track(phone, anchor)
        track(tablet, anchor)
        compose.mainClock.advanceTimeBy(100)
        assertTrue(motion.gliding)
        val xBefore = displayedX()
        val yBefore = displayedY()

        track(wide, anchor)
        assertEquals("no jump when interrupted", xBefore, displayedX(), TOLERANCE)
        assertEquals("no jump when interrupted", yBefore, displayedY(), TOLERANCE)
        assertTrue(motion.gliding)
        compose.mainClock.advanceTimeBy(BifolioMotion.CONTINUITY_DURATION_MILLIS + FRAME_SLACK)
        assertFalse(motion.gliding)
        assertEquals(checkNotNull(motion.spot).x, displayedX(), TOLERANCE)
        assertEquals(checkNotNull(motion.spot).y, displayedY(), TOLERANCE)
    }

    @Test
    fun touchCancelsTheGlideAtOnceWithoutBlockingInput() {
        start()
        val anchor = midAnchor()
        track(phone, anchor)
        track(tablet, anchor)
        compose.mainClock.advanceTimeBy(60)
        motion.cancel()
        assertFalse(motion.gliding)
        assertEquals(0f, motion.offsetX, 0f)
        assertEquals(0f, motion.offsetY, 0f)
    }

    @Test
    fun reducedMotionSwapsInstantlyAndKeepsTheHighlightOnly() {
        start()
        val anchor = midAnchor()
        track(phone, anchor)
        track(tablet, anchor, reduced = true)
        assertFalse(motion.gliding)
        assertEquals(0f, motion.offsetX, 0f)
        assertEquals(0f, motion.offsetY, 0f)
        assertEquals(BifolioMotion.ANCHOR_GLOW_ALPHA, motion.glow, 0f)
        compose.mainClock.advanceTimeBy(BifolioMotion.ANCHOR_GLOW_DURATION_MILLIS + FRAME_SLACK)
        assertEquals(0f, motion.glow, 0f)
    }

    @Test
    fun sameRequestDoesNotMove() {
        start()
        track(phone, midAnchor())
        track(phone, phone.flow.pages[1].anchor)
        assertFalse(motion.gliding)
        assertEquals(0f, motion.glow, 0f)
        assertNotNull(motion.spot)
    }
}
