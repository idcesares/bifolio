package dev.dcesares.bifolio.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/**
 * Padding that keeps content inside `WindowInsets.safeDrawing` (system bars, cutouts, keyboard) while the
 * surface itself draws edge to edge (LAY-03). [horizontal] and [vertical] are added on top of the insets.
 */
@Composable
public fun safeDrawingPadding(
    horizontal: Dp,
    vertical: Dp,
): PaddingValues {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = insets.calculateStartPadding(direction) + horizontal,
        end = insets.calculateEndPadding(direction) + horizontal,
        top = insets.calculateTopPadding() + vertical,
        bottom = insets.calculateBottomPadding() + vertical,
    )
}
