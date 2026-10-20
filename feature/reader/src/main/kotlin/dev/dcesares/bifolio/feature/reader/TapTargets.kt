package dev.dcesares.bifolio.feature.reader

import androidx.compose.ui.geometry.Rect
import dev.dcesares.bifolio.layout.TargetKind
import kotlin.math.abs

/**
 * Where a link or footnote marker sits, in px from some origin (a page column, a text layout). The rectangle is
 * what is drawn; the tap area is [minSide] wide and tall at least for a marker, centered on it (A11Y-02), which
 * is a hit area only: nothing is added to the text, so layout and pagination do not change.
 */
internal class TargetBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val kind: TargetKind,
    val value: String,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x in left..right && y in top..bottom

    /** The tap area: markers grow to [minSide] around their center, links are tapped where they are drawn. */
    fun reaches(
        x: Float,
        y: Float,
        minSide: Float,
    ): Boolean {
        if (kind != TargetKind.NOTE) return contains(x, y)
        val halfW = maxOf(right - left, minSide) / 2f
        val halfH = maxOf(bottom - top, minSide) / 2f
        return abs(x - centerX) <= halfW && abs(y - centerY) <= halfH
    }

    fun shifted(
        dx: Float,
        dy: Float,
    ): TargetBox = TargetBox(left + dx, top + dy, right + dx, bottom + dy, kind, value)
}

/**
 * The target a tap at ([x], [y]) means, or null. A target drawn under the finger wins; otherwise the enlarged area
 * of the nearest marker center. So a marker next to a link never steals a tap that landed on the link text.
 */
internal fun pickTarget(
    boxes: List<TargetBox>,
    x: Float,
    y: Float,
    minSide: Float,
): TargetBox? {
    var under: TargetBox? = null
    var near: TargetBox? = null
    var nearDistance = Float.MAX_VALUE
    for (box in boxes) {
        if (box.contains(x, y)) {
            if (under == null || box.kind == TargetKind.NOTE) under = box
        } else if (box.reaches(x, y, minSide)) {
            val d = abs(x - box.centerX) + abs(y - box.centerY)
            if (d < nearDistance) {
                nearDistance = d
                near = box
            }
        }
    }
    return under ?: near
}

/**
 * A tap on a footnote marker: its [label] and where the marker is ([bounds], px from the top-left of the reading
 * surface), so a wide window can put the popover next to it (UI-09).
 */
internal class NoteTap(
    val label: String,
    val bounds: Rect,
)
