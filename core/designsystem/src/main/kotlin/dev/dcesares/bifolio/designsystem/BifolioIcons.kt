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
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide icons (ISC, see docs/design/ICONS.md), 24dp viewport, 1.5 stroke (UI-12). Path data is the official
 * Lucide geometry, unmodified; only the stroke width differs from Lucide's default of 2.
 */
public object BifolioIcons {
    private const val VIEWPORT = 24f
    private const val STROKE = 1.5f

    private fun icon(
        name: String,
        vararg paths: String,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, VIEWPORT, VIEWPORT)
            .apply {
                paths.forEach { d ->
                    // A fresh parser per path: toNodes() returns the parser's own list, which the next parse clears.
                    addPath(
                        pathData = PathParser().parsePathString(d).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = STROKE,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    public val BookOpen: ImageVector =
        icon(
            "book-open",
            "M12 7v14",
            "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1" +
                "v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z",
        )

    public val FolderOpen: ImageVector =
        icon(
            "folder-open",
            "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5" +
                "a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2",
        )

    public val Trash: ImageVector =
        icon(
            "trash",
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )

    public val Info: ImageVector =
        icon(
            "info",
            "M22 12A10 10 0 0 1 12 22A10 10 0 0 1 2 12A10 10 0 0 1 12 2A10 10 0 0 1 22 12z",
            "M12 16v-4",
            "M12 8h.01",
        )

    public val ChevronRight: ImageVector =
        icon(
            "chevron-right",
            "m9 18 6-6-6-6",
        )

    public val ChevronDown: ImageVector =
        icon(
            "chevron-down",
            "m6 9 6 6 6-6",
        )

    public val ChevronsUpDown: ImageVector =
        icon(
            "chevrons-up-down",
            "m7 15 5 5 5-5",
            "m7 9 5-5 5 5",
        )

    public val Undo2: ImageVector =
        icon(
            "undo-2",
            "M9 14 4 9l5-5",
            "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
        )

    public val List: ImageVector =
        icon(
            "list",
            "M3 5h.01",
            "M3 12h.01",
            "M3 19h.01",
            "M8 5h13",
            "M8 12h13",
            "M8 19h13",
        )

    public val Type: ImageVector =
        icon(
            "type",
            "M12 4v16",
            "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
            "M9 20h6",
        )

    public val ChevronLeft: ImageVector =
        icon(
            "chevron-left",
            "m15 18-6-6 6-6",
        )
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
