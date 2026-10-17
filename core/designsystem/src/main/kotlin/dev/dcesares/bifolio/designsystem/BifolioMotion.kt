package dev.dcesares.bifolio.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
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
    private const val FADE_MILLIS = 180

    /** Duration of the Fade page turn and of every reduced-motion fallback (MOT-03, MOT-07). */
    public const val FADE_DURATION_MILLIS: Int = FADE_MILLIS

    /** The incoming spread leads the outgoing one by this fraction of the page width (MOT-02). */
    public const val PARALLAX_FRACTION: Float = 0.08f

    /** Page turns, reflow and scroll snapping: spatial spring, no overshoot. */
    public fun <T> reading(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = READING_STIFFNESS)

    /** Fade page turn and reduced motion: 180 ms, emphasized easing. */
    public fun <T> fade(): FiniteAnimationSpec<T> = tween(FADE_MILLIS, easing = EMPHASIZED)

    private val EMPHASIZED = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
