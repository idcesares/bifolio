package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.TargetKind
import dev.dcesares.bifolio.layout.TextRole
import kotlin.math.roundToInt

/** Links and markers of the columns of spread [index], in px from the top-left of the surface. */
private fun targetsOf(
    layout: PagedLayout,
    store: SpreadStore,
    index: Int,
    originX: Float,
    originY: Float,
): List<TargetBox> {
    val geometry = layout.geometry
    val all = ArrayList<TargetBox>(0)
    for ((column, page) in layout.flow.pagesOfSpread(index).withIndex()) {
        val prepared = store.columns[page] ?: continue
        val dx = originX + geometry.columnLefts[column]
        val dy = originY + geometry.verticalMargin
        for (box in prepared.targets) all.add(box.shifted(dx, dy))
    }
    return all
}

/**
 * The link or marker a tap at [position] means on the shown spread, or null (NAV-07, UI-09). Window coordinates, the
 * same way as [tableAt]: the column origin is the inset plus the column's left, plus the vertical margin.
 */
internal fun targetAt(
    layout: PagedLayout?,
    store: SpreadStore?,
    index: Int,
    position: Offset,
    originX: Float,
    originY: Float,
    minSide: Float,
): TargetBox? {
    if (layout == null || store == null || index < 0) return null
    return pickTarget(targetsOf(layout, store, index, originX, originY), position.x, position.y, minSide)
}

/** Column spacing of the reading order: a column is read whole, whatever the heights in the next one are. */
private const val COLUMN_ORDER_STEP = 100_000f

/** Position in the reading order of something [top] px down column [column] (A11Y-01). */
internal fun readingOrder(
    column: Int,
    top: Float,
): Float = column * COLUMN_ORDER_STEP + top

/** One empty, pointer-free node over [x], [y] (px from the surface) with the given size. */
@Composable
private fun SemanticsBox(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    semantics: SemanticsPropertyReceiver.() -> Unit,
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .semantics(properties = semantics),
    )
}

/**
 * What TalkBack finds on the shown spread (A11Y-01, A11Y-04). The page is a canvas, so none of it has a node of its
 * own; each text, image, formula and footnote marker gets an empty node over its box, and none takes pointer
 * input, so the page gestures keep working under them.
 *
 * - Text: the visible lines as text, headings marked as headings. Footnote labels are left out of it, the markers
 *   have nodes of their own.
 * - Images: the alt text, or a plain "Image" when there is none.
 * - Formulas: the TeX source, or the failure text and the source where it could not be drawn.
 * - Markers: a touch-sized button that opens the note (A11Y-02).
 *
 * Reading order is declared, not left to the geometry: a column is read from top to bottom before the next
 * column starts, a marker comes right after the text it stands in.
 */
@Composable
internal fun PagedPageSemantics(
    layout: PagedLayout,
    store: SpreadStore,
    index: Int,
    originX: Float,
    originY: Float,
    renderer: MathRenderer?,
    onNote: (NoteTap) -> Unit,
) {
    if (index < 0) return
    // Read so a formula found to fail while it is shown changes its description.
    renderer?.observe()
    val failedText = stringResource(R.string.reader_math_failed)
    val noAlt = stringResource(R.string.reader_image_no_alt)
    val side = with(LocalDensity.current) { Spacing.TouchTarget.toPx() }
    val geometry = layout.geometry
    Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        for ((column, page) in layout.flow.pagesOfSpread(index).withIndex()) {
            val prepared = store.columns[page] ?: continue
            val dx = originX + geometry.columnLefts[column]
            val dy = originY + geometry.verticalMargin
            for (text in prepared.texts) {
                val spoken = text.spoken
                if (spoken.isEmpty()) continue
                SemanticsBox(dx + text.x, dy + text.top, text.width, text.height) {
                    this.text = AnnotatedString(spoken)
                    traversalIndex = readingOrder(column, text.top)
                    if (text.role.isHeading()) heading()
                }
            }
            for (image in prepared.images) {
                val description = image.block.alt.ifBlank { noAlt }
                SemanticsBox(dx + image.x, dy + image.y, image.width, image.height) {
                    contentDescription = description
                    role = Role.Image
                    traversalIndex = readingOrder(column, image.y)
                }
            }
            for (box in prepared.maths) {
                val tex = box.block.tex
                val description = if (renderer?.hasFailed(box.block) == true) "$failedText $tex" else tex
                SemanticsBox(dx + box.x, dy + box.y, box.width, box.height) {
                    contentDescription = description
                    traversalIndex = readingOrder(column, box.y)
                }
            }
            for (box in prepared.targets) {
                if (box.kind != TargetKind.NOTE) continue
                val marker = box.shifted(dx, dy)
                val description = stringResource(R.string.reader_footnote_marker, marker.value)
                val width = maxOf(marker.right - marker.left, side)
                val height = maxOf(marker.bottom - marker.top, side)
                SemanticsBox(marker.centerX - width / 2, marker.centerY - height / 2, width, height) {
                    contentDescription = description
                    role = Role.Button
                    traversalIndex = readingOrder(column, box.top)
                    onClick {
                        onNote(marker.toNoteTap())
                        true
                    }
                }
            }
        }
    }
}

private fun TextRole.isHeading(): Boolean =
    when (this) {
        TextRole.HEADING_1,
        TextRole.HEADING_2,
        TextRole.HEADING_3,
        TextRole.HEADING_4,
        TextRole.HEADING_5,
        TextRole.HEADING_6,
        -> true
        else -> false
    }

/** The tap a marker box stands for. */
internal fun TargetBox.toNoteTap(): NoteTap = NoteTap(value, Rect(left, top, right, bottom))
