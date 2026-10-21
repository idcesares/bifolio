package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.MathSize
import dev.dcesares.bifolio.model.MathBlock

/** The formulas of the open document; null where there are none to draw (a popover, a preview): the source shows. */
internal val LocalMathRenderer = staticCompositionLocalOf<MathRenderer?> { null }

private const val VIEWPORT_FRACTION = 0.85f
private const val PLACEHOLDER_WIDTH = 16f
private const val PLACEHOLDER_HEIGHT = 5f
private val MARK_SIZE = 16.dp

/**
 * A display formula in the scroll surface (SYN-12, PAG-07): drawn in the theme ink, centered, at most the column
 * wide and 85% of the viewport high, never scaled up, the same box rule as the page surface (`AtomicFit`). Where it
 * fails, or while no renderer exists, the TeX source shows in the code style with a quiet error mark. The TeX
 * source is the content description (A11Y-04), whether or not the formula drew.
 */
@Composable
internal fun MathView(
    block: MathBlock,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalMathRenderer.current
    val palette = BifolioTheme.palette
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current.density
    val described = modifier.clearAndSetSemantics { contentDescription = block.tex }
    if (renderer == null) {
        CodeView(block.tex, described)
        return
    }
    // Read in composition so a landed bitmap or a failure recomposes this block.
    renderer.observe()
    val sizes = renderer.sizes.collectAsState().value
    val known = sizes.sizeOf(block) as? MathSize.Known
    if (renderer.hasFailed(block)) {
        val failedText = stringResource(R.string.reader_math_failed)
        val failedDescribed = modifier.clearAndSetSemantics { contentDescription = "$failedText ${block.tex}" }
        // The box is never shorter than the placeholder it replaces, so a late failure moves little; a long source
        // wraps and is shown whole, never cut.
        BoxWithConstraints(failedDescribed.fillMaxWidth()) {
            val placeholderHeight = maxWidth * (PLACEHOLDER_HEIGHT / PLACEHOLDER_WIDTH)
            CodeView(block.tex, Modifier.fillMaxWidth().heightIn(min = placeholderHeight))
            BifolioIcon(
                BifolioIcons.CircleAlert,
                contentDescription = null,
                tint = palette.inkMuted,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.S)
                        .size(MARK_SIZE),
            )
        }
        return
    }
    BoxWithConstraints(described.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        if (known == null) {
            val heightPx = maxWidthPx * PLACEHOLDER_HEIGHT / PLACEHOLDER_WIDTH
            Canvas(Modifier.fillMaxWidth().size(width = (maxWidthPx / density).dp, height = (heightPx / density).dp)) {
                val corner = CornerRadius(Spacing.CodeRadius.toPx())
                drawRoundRect(palette.codeSurface, cornerRadius = corner)
                drawRoundRect(palette.rule, cornerRadius = corner, style = Stroke(Spacing.Rule.toPx()))
            }
            return@BoxWithConstraints
        }
        val (widthPx, heightPx) = fittedMathBox(known, maxWidthPx, windowHeightPx * VIEWPORT_FRACTION)
        val bitmap = renderer.bitmapFor(block, widthPx, heightPx)
        val box = Modifier.size((widthPx / density).dp, (heightPx / density).dp)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = box,
                colorFilter = ColorFilter.tint(palette.ink),
            )
        } else {
            Box(box)
        }
    }
}
