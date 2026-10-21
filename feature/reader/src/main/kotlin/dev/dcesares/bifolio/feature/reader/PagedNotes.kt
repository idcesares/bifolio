package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.TargetKind
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

/** The display formulas of the columns of spread [index], in px from the top-left of the surface. */
internal fun mathBoxesOf(
    layout: PagedLayout,
    store: SpreadStore,
    index: Int,
    originX: Float,
    originY: Float,
): List<MathBox> {
    val geometry = layout.geometry
    val all = ArrayList<MathBox>(0)
    for ((column, page) in layout.flow.pagesOfSpread(index).withIndex()) {
        val prepared = store.columns[page] ?: continue
        val dx = originX + geometry.columnLefts[column]
        val dy = originY + geometry.verticalMargin
        for (box in prepared.maths) all.add(MathBox(box.block, box.x + dx, box.y + dy, box.width, box.height))
    }
    return all
}

/**
 * TalkBack nodes for the formulas of the shown spread (A11Y-04). The page is a canvas, so a formula has no node of
 * its own; each gets an empty node over its box whose description is the TeX source, or the failure text and the
 * source where the formula could not be drawn. Like the footnote nodes, they take no pointer input.
 */
@Composable
internal fun PagedMathSemantics(
    layout: PagedLayout,
    store: SpreadStore,
    index: Int,
    originX: Float,
    originY: Float,
    renderer: MathRenderer?,
) {
    if (index < 0) return
    val density = LocalDensity.current
    // Read so a formula found to fail while it is shown changes its description.
    renderer?.observe()
    val failedText = stringResource(R.string.reader_math_failed)
    for (box in mathBoxesOf(layout, store, index, originX, originY)) {
        val description = if (renderer?.hasFailed(box.block) == true) "$failedText ${box.block.tex}" else box.block.tex
        Box(
            Modifier
                .offset { IntOffset(box.x.roundToInt(), box.y.roundToInt()) }
                .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                .semantics { contentDescription = description },
        )
    }
}

/** The tap a marker box stands for. */
internal fun TargetBox.toNoteTap(): NoteTap = NoteTap(value, Rect(left, top, right, bottom))

/**
 * TalkBack nodes for the footnote markers of the shown spread. The page is painted on a canvas, so a marker has no
 * node of its own; each gets an empty, touch-sized node with a description and a click action (A11Y-02). They
 * carry no pointer input, so the page gestures keep working under them.
 */
@Composable
internal fun PagedNoteSemantics(
    layout: PagedLayout,
    store: SpreadStore,
    index: Int,
    originX: Float,
    originY: Float,
    onNote: (NoteTap) -> Unit,
) {
    if (index < 0) return
    val density = LocalDensity.current
    val side = with(density) { Spacing.TouchTarget.toPx() }
    val markers = targetsOf(layout, store, index, originX, originY).filter { it.kind == TargetKind.NOTE }
    for (marker in markers) {
        val description = stringResource(R.string.reader_footnote_marker, marker.value)
        val width = maxOf(marker.right - marker.left, side)
        val height = maxOf(marker.bottom - marker.top, side)
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (marker.centerX - width / 2).roundToInt(),
                        (marker.centerY - height / 2).roundToInt(),
                    )
                }.size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        onNote(marker.toNoteTap())
                        true
                    }
                },
        )
    }
}
