package dev.dcesares.bifolio.feature.reader

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import kotlinx.coroutines.CancellationException

private const val SHRINK = 0.12f
private const val FADE = 0.35f
private val CornerRadius = 28.dp

/**
 * Predictive back from the reader to the library (NAV-06). While the gesture is held the reader shrinks and
 * fades, rounding its corners, and lets go of the screen once [onBack] runs. Cancelling the gesture springs it
 * back. With reduced motion (MOT-07) there is no shrink: back simply goes back. The gesture never touches the
 * reading position (MOT-09).
 */
@Composable
public fun ReaderBackTransition(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    var progress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler { events ->
        try {
            events.collect { event: BackEventCompat -> if (!reduced) progress = event.progress }
            onBack()
        } catch (cancelled: CancellationException) {
            animate(progress, 0f, animationSpec = BifolioMotion.reading()) { value, _ -> progress = value }
            throw cancelled
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = 1f - SHRINK * progress
                scaleX = scale
                scaleY = scale
                alpha = 1f - FADE * progress
                shape = RoundedCornerShape(CornerRadius * progress)
                clip = progress > 0f
            },
    ) {
        content()
    }
}
