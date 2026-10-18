package dev.dcesares.bifolio.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens of the reading surface (DESIGN section 6, MOT-01). Feature code takes every spring and
 * duration from here.
 *
 * `reading` is the critically damped counterpart of the `MotionScheme.standard()` spatial spring: no
 * overshoot, text never bounces (MOT-02). Material 3 joins the build in M4 with the chrome; this token then
 * reads its values from the scheme without changing callers.
 */
public object BifolioMotion {
    private const val READING_STIFFNESS = 700f
    private const val CHROME_STIFFNESS = 450f
    private const val CHROME_DAMPING = 0.65f
    private const val CHROME_EXIT_MILLIS = 110
    private const val FADE_MILLIS = 180
    private const val CONTINUITY_MILLIS = 250
    private const val ANCHOR_GLOW_MILLIS = 600

    /** Duration of the Fade page turn and of every reduced-motion fallback (MOT-03, MOT-07). */
    public const val FADE_DURATION_MILLIS: Int = FADE_MILLIS

    /** The incoming spread leads the outgoing one by this fraction of the page width (MOT-02). */
    public const val PARALLAX_FRACTION: Float = 0.08f

    /** The anchor line glides from its old rectangle to its new one in about 250 ms (MOT-05). */
    public const val CONTINUITY_DURATION_MILLIS: Int = CONTINUITY_MILLIS

    /** The soft highlight on the anchor line fades over 600 ms (MOT-05). */
    public const val ANCHOR_GLOW_DURATION_MILLIS: Int = ANCHOR_GLOW_MILLIS

    /** Opacity the anchor highlight starts from (DESIGN motion.anchorGlow). */
    public const val ANCHOR_GLOW_ALPHA: Float = 0.6f

    /** Page turns, reflow and scroll snapping: spatial spring, no overshoot. */
    public fun <T> reading(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = READING_STIFFNESS)

    /** Chrome entrance: expressive spring, slight overshoot allowed (MOT-06). */
    public fun <T> chromeEnter(): FiniteAnimationSpec<T> =
        spring(dampingRatio = CHROME_DAMPING, stiffness = CHROME_STIFFNESS)

    /** Chrome exit: shorter than the entrance and without overshoot (MOT-06). */
    public fun <T> chromeExit(): FiniteAnimationSpec<T> = tween(CHROME_EXIT_MILLIS, easing = EMPHASIZED)

    /** Fade page turn and reduced motion: 180 ms, emphasized easing. */
    public fun <T> fade(): FiniteAnimationSpec<T> = tween(FADE_MILLIS, easing = EMPHASIZED)

    /** Fold, unfold, posture and rotation: the anchor line moves to its new place, no overshoot (MOT-05). */
    public fun <T> continuity(): FiniteAnimationSpec<T> = tween(CONTINUITY_MILLIS, easing = EMPHASIZED)

    /** Highlight fade on the anchor line, from [ANCHOR_GLOW_ALPHA] to 0 (MOT-05). */
    public fun <T> anchorGlow(): FiniteAnimationSpec<T> = tween(ANCHOR_GLOW_MILLIS, easing = LinearEasing)

    private val EMPHASIZED = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
