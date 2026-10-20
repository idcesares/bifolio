package dev.dcesares.bifolio.feature.reader

import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.ImageSize
import dev.dcesares.bifolio.model.ImageBlock

/** The pixels of the open document's images; null where there are none to draw (a popover, a preview). */
internal val LocalImageRenderer = staticCompositionLocalOf<ImageRenderer?> { null }

private const val VIEWPORT_FRACTION = 0.85f
private const val PLACEHOLDER_WIDTH = 16f
private const val PLACEHOLDER_HEIGHT = 9f
private const val ALT_MAX_LINES = 6

/**
 * An image in the scroll surface (SYN-08, PAG-07): at most the column wide and 85% of the viewport high, never
 * scaled up, with the placeholder frame and the alt text where there are no pixels. The page surface paints the same
 * thing with [ColumnPainter]; both use the box rule of the layout engine (`AtomicFit`).
 */
@Composable
internal fun ImageView(
    block: ImageBlock,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalImageRenderer.current
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val density = LocalDensity.current.density
    // The window the reader sits in (not the screen): split screen and multi-window shrink it (LAY-01).
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val dst = remember { android.graphics.RectF() }
    val description =
        block.alt.ifBlank { stringResource(R.string.reader_image_no_alt) }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = windowHeightPx * VIEWPORT_FRACTION
        val sizes = renderer?.sizes?.collectAsState()?.value
        val known = sizes?.sizeOf(block) as? ImageSize.Known
        val widthPx: Int
        val heightPx: Int
        if (known != null) {
            val scale = minOf(1f, maxWidthPx / (known.width * density), maxHeightPx / (known.height * density))
            widthPx = (known.width * density * scale).toInt().coerceAtLeast(1)
            heightPx = (known.height * density * scale).toInt().coerceAtLeast(1)
        } else {
            widthPx = maxWidthPx.toInt()
            heightPx = (maxWidthPx * PLACEHOLDER_HEIGHT / PLACEHOLDER_WIDTH).toInt()
        }
        // Read in composition so a landed or failed decode recomposes this block.
        renderer?.observe()
        val showAlt = renderer == null || renderer.showsAlt(block, widthPx, heightPx)
        val radius = Spacing.CodeRadius
        Box(
            Modifier
                .size(widthPx.toDp(density), heightPx.toDp(density))
                // The alt text is drawn too: describe the picture once, not the text again.
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Image
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                renderer?.observe()
                val bitmap = if (known != null) renderer?.bitmapFor(block, widthPx, heightPx) else null
                if (bitmap != null) {
                    drawIntoCanvas { c ->
                        dst.set(0f, 0f, size.width, size.height)
                        c.nativeCanvas.drawBitmap(bitmap, null, dst, paint)
                    }
                } else {
                    val corner = CornerRadius(radius.toPx())
                    drawRoundRect(palette.codeSurface, cornerRadius = corner)
                    drawRoundRect(palette.rule, cornerRadius = corner, style = Stroke(Spacing.Rule.toPx()))
                }
            }
            if (showAlt && block.alt.isNotBlank()) {
                BasicText(
                    block.alt,
                    style = type.caption.copy(color = palette.ink),
                    maxLines = ALT_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(Spacing.M),
                )
            }
        }
    }
}

private fun Int.toDp(density: Float) = (this / density).dp

/**
 * The offer to pick the folder of a document whose images are relative paths (UI-14). One tap opens the system
 * folder chooser at the document's folder; the cross dismisses it for good. Plain surface with a rule border, no
 * shadow and no motion (DESIGN.md), so it needs no reduced-motion path (MOT-07).
 */
@Composable
internal fun ImageFolderBanner(
    banner: ImageBanner,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banner == ImageBanner.Hidden) return
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            onPick(uri?.toString())
        }
    val start =
        when (banner) {
            is ImageBanner.Offer -> banner.pickerStart
            is ImageBanner.WrongFolder -> banner.pickerStart
            ImageBanner.Hidden -> null
        }
    val wrong = banner is ImageBanner.WrongFolder
    val message = stringResource(if (wrong) R.string.reader_images_wrong_folder else R.string.reader_images_offer)
    val shape = RoundedCornerShape(Spacing.CodeRadius)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = Spacing.S)
                .clip(shape)
                .background(palette.codeSurface)
                .border(Spacing.Rule, palette.rule, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button) { launcher.launch(start?.let(Uri::parse)) }
                .defaultMinSize(minHeight = Spacing.TouchTarget)
                .padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            BifolioIcon(BifolioIcons.FolderOpen, contentDescription = null, tint = palette.accent)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                BasicText(message, style = type.caption.copy(color = palette.ink))
                BasicText(
                    stringResource(R.string.reader_images_choose),
                    style = type.caption.copy(color = palette.accent, fontWeight = FontWeight.SemiBold),
                )
            }
        }
        BifolioIconButton(
            icon = BifolioIcons.X,
            description = stringResource(R.string.reader_images_dismiss),
            onClick = onDismiss,
        )
    }
}
