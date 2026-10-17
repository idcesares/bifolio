package dev.dcesares.bifolio.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Minimal outlined icons, 24dp viewport, 1.5 stroke (UI-12).
 * PENDING M4 (OPN-04): replace these hand-drawn vectors with the Lucide set once its license is recorded.
 */
public object BifolioIcons {
    private const val VIEWPORT = 24f
    private const val STROKE = 1.5f

    private fun icon(
        name: String,
        block: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, VIEWPORT, VIEWPORT)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            ).build()

    /** Two leaves meeting at a spine. */
    public val BookOpen: ImageVector =
        icon("book-open") {
            moveTo(12f, 6.5f)
            lineTo(12f, 19.5f)
            moveTo(12f, 6.5f)
            curveTo(10.5f, 5f, 7.5f, 4.5f, 3.5f, 5f)
            lineTo(3.5f, 18f)
            curveTo(7.5f, 17.5f, 10.5f, 18f, 12f, 19.5f)
            curveTo(13.5f, 18f, 16.5f, 17.5f, 20.5f, 18f)
            lineTo(20.5f, 5f)
            curveTo(16.5f, 4.5f, 13.5f, 5f, 12f, 6.5f)
        }

    public val FolderOpen: ImageVector =
        icon("folder-open") {
            moveTo(3.5f, 18.5f)
            lineTo(3.5f, 6f)
            lineTo(9f, 6f)
            lineTo(11f, 8f)
            lineTo(18f, 8f)
            lineTo(18f, 10.5f)
            moveTo(3.5f, 18.5f)
            lineTo(6f, 10.5f)
            lineTo(21f, 10.5f)
            lineTo(18.5f, 18.5f)
            close()
        }

    public val Trash: ImageVector =
        icon("trash-2") {
            moveTo(4f, 6.5f)
            lineTo(20f, 6.5f)
            moveTo(6f, 6.5f)
            lineTo(7f, 20f)
            lineTo(17f, 20f)
            lineTo(18f, 6.5f)
            moveTo(9f, 6.5f)
            lineTo(9f, 4f)
            lineTo(15f, 4f)
            lineTo(15f, 6.5f)
            moveTo(10f, 10.5f)
            lineTo(10f, 16f)
            moveTo(14f, 10.5f)
            lineTo(14f, 16f)
        }

    public val Info: ImageVector =
        icon("info") {
            moveTo(21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            close()
            moveTo(12f, 11f)
            lineTo(12f, 16.5f)
            moveTo(12f, 7.75f)
            lineTo(12f, 8f)
        }

    public val ChevronLeft: ImageVector =
        icon("chevron-left") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
}

/** Draws [icon] at 24dp in [tint]. Decorative when [contentDescription] is null. */
@Composable
public fun BifolioIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(24.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}
