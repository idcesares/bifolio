package dev.dcesares.bifolio.feature.reader

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import kotlinx.coroutines.CancellationException

private val OriginRadius = 12.dp
private const val FALLBACK_INSET = 0.12f
private const val CONTENT_FADE_START = 0.3f
private const val CONTENT_FADE_SPAN = 0.5f

/**
 * Library to reader (MOT-04): the reader grows out of the recent item that was tapped, a container transform
 * that clips the reader to a rectangle running from the item to the full screen. The reader is laid out at its
 * final size the whole time, so the animation never repaginates.
 *
 * Predictive back (NAV-06) drives the same progress backwards: while the gesture is held the reader shrinks
 * toward [origin]; cancelling springs it back, and committing hands the rest of the way to the exit animation.
 * Without an [origin] (the picker, or an external open) the reader scales in from the middle of the screen.
 * With reduced motion (MOT-07) the reader only fades. Nothing here touches the reading position (MOT-09).
 */
@Composable
public fun ContainerTransform(
    detailVisible: Boolean,
    origin: Rect?,
    onBack: () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val expand = remember { Animatable(if (detailVisible) 1f else 0f) }
    var back by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(detailVisible) {
        expand.animateTo(if (detailVisible) 1f else 0f, BifolioMotion.container())
    }
    if (detailVisible) {
        PredictiveBackHandler { events ->
            try {
                events.collect { event: BackEventCompat -> if (!reduced) back = event.progress }
                // Keep the position the gesture reached, then let the exit animation finish the job.
                expand.snapTo(expand.value * (1f - back))
                back = 0f
                onBack()
            } catch (cancelled: CancellationException) {
                animate(back, 0f, animationSpec = BifolioMotion.reading()) { value, _ -> back = value }
                throw cancelled
            }
        }
    }
    val covered = detailVisible && expand.value >= 1f && back == 0f
    Box(Modifier.fillMaxSize()) {
        if (!covered) list()
        if (detailVisible || expand.value > 0f) {
            val density = LocalDensity.current
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = (expand.value * (1f - back)).coerceIn(0f, 1f)
                        this.shape = ContainerShape(origin, density, reduced, progress)
                        clip = progress < 1f
                        alpha = if (origin == null || reduced) progress else 1f
                    }.background(BifolioTheme.palette.page),
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val progress = (expand.value * (1f - back)).coerceIn(0f, 1f)
                        alpha = ((progress - CONTENT_FADE_START) / CONTENT_FADE_SPAN).coerceIn(0f, 1f)
                    },
                ) {
                    detail()
                }
            }
        }
    }
}

/** Rectangle running from [origin] (or a slightly inset screen when there is none) to the whole screen. */
private class ContainerShape(
    private val origin: Rect?,
    private val density: Density,
    private val reduced: Boolean,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val full = Rect(0f, 0f, size.width, size.height)
        if (reduced) return Outline.Rectangle(full)
        val start =
            origin ?: Rect(
                size.width * FALLBACK_INSET,
                size.height * FALLBACK_INSET,
                size.width * (1f - FALLBACK_INSET),
                size.height * (1f - FALLBACK_INSET),
            )
        val rect = lerp(start, full, progress)
        val radius = with(this.density) { OriginRadius.toPx() } * (1f - progress)
        return Outline.Rounded(RoundRect(rect, radius, radius))
    }
}
