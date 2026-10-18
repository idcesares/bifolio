package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing

/** Progress hairline (UI-05): a 2dp line at the foot of the spread, filled to [progress]. Decorative. */
@Composable
internal fun ProgressHairline(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    Canvas(modifier.fillMaxWidth().height(Spacing.Hairline)) {
        drawRect(palette.rule, Offset.Zero, size)
        drawRect(palette.accent, Offset.Zero, Size(size.width * progress.coerceIn(0f, 1f), size.height))
    }
}
