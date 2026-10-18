package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.core.animate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import dev.dcesares.bifolio.data.TurnChoice
import dev.dcesares.bifolio.designsystem.BifolioMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** How a page turn looks (MOT-02, MOT-03). Slide follows the finger; Fade and None turn after the gesture. */
internal enum class PageTurnStyle { SLIDE, FADE, NONE }

/** Fraction of the page a drag must cover to turn, and the flick speed in page widths per second. */
private const val COMMIT_FRACTION = 0.35f
private const val FLING_PAGES_PER_SECOND = 1.2f

/**
 * Page turn state machine. [progress] runs from -1 (the next spread fully in) to +1 (the previous one), 0 at rest.
 * The surface draws from it and never recomposes while it animates. A touch interrupts any running animation
 * at its current value and continues from there (MOT-09); only a settled turn calls [commit].
 *
 * Directions are `-1` for forward (the finger moves left) and `+1` for backward.
 */
internal class PageTurnController(
    private val scope: CoroutineScope,
) {
    /** Signed turn progress. Read it from draw code only. */
    var progress by mutableFloatStateOf(0f)
        private set

    var width: Float = 1f
    var style: PageTurnStyle = PageTurnStyle.SLIDE
    var canTurn: (Int) -> Boolean = { false }
    var commit: (Int) -> Unit = {}

    private var job: Job? = null
    private var heading = 0

    val followsFinger: Boolean get() = style == PageTurnStyle.SLIDE

    /** Stops the running animation where it is. Returns the direction it was turning to, or 0. */
    fun grab(): Int {
        job?.cancel()
        job = null
        return heading.also { heading = 0 }
    }

    /** Applies a finger movement of [dx] px to a following turn. */
    fun drag(dx: Float) {
        if (!followsFinger) return
        val next = (progress + dx / width).coerceIn(-1f, 1f)
        val dir = sign(next).toInt()
        progress = if (dir == 0 || canTurn(dir)) next else 0f
    }

    /** The finger lifted after a drag of [totalDx] px at [velocity] px/s. */
    fun release(
        velocity: Float,
        totalDx: Float,
    ) {
        val dir = if (followsFinger) sign(progress).toInt() else sign(totalDx).toInt()
        if (dir == 0) return
        val flick = abs(velocity) / width > FLING_PAGES_PER_SECOND && sign(velocity).toInt() == dir
        val against = abs(velocity) / width > FLING_PAGES_PER_SECOND && sign(velocity).toInt() == -dir
        val far = if (followsFinger) abs(progress) > COMMIT_FRACTION else abs(totalDx) / width > COMMIT_FRACTION / 2
        val turning = (flick || far) && !against && canTurn(dir)
        settle(if (turning) dir else 0, velocity)
    }

    /** A tap or key asked for a turn. */
    fun turn(dir: Int) {
        if (canTurn(dir)) settle(dir, 0f)
    }

    /** Finishes the turn that was heading to [dir] at once. Used when a new input arrives during it. */
    fun commitNow(dir: Int) {
        commit(dir)
        progress = 0f
    }

    fun reset() {
        progress = 0f
    }

    private fun settle(
        target: Int,
        velocity: Float,
    ) {
        heading = target
        job =
            scope.launch {
                if (style != PageTurnStyle.NONE) {
                    val slide = style == PageTurnStyle.SLIDE
                    animate(
                        initialValue = progress,
                        targetValue = target.toFloat(),
                        initialVelocity = if (slide) velocity / width else 0f,
                        animationSpec = if (slide) BifolioMotion.reading() else BifolioMotion.fade(),
                    ) { value, _ -> progress = value }
                }
                if (target != 0) commit(target)
                progress = 0f
                heading = 0
            }
    }
}

internal fun turnStyleOf(choice: TurnChoice): PageTurnStyle =
    when (choice) {
        TurnChoice.SLIDE -> PageTurnStyle.SLIDE
        TurnChoice.FADE -> PageTurnStyle.FADE
        TurnChoice.NONE -> PageTurnStyle.NONE
    }
