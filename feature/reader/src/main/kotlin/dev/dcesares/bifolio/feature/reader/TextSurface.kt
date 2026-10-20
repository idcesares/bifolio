package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.TargetKind

/** Layout and position of one text, filled in while it is measured and placed; read when a tap arrives. */
internal class TextSurface {
    var layout: TextLayoutResult? = null
    var coordinates: LayoutCoordinates? = null
}

/**
 * Makes the footnote markers of [text] tappable on the scroll surface (UI-09). A marker is a small superscript, so
 * its tap area grows to the touch target (A11Y-02) around it, as a hit area only: the text is not padded and its
 * layout does not move. A tap that lands on a link first is the link's (it consumes the release), a tap anywhere
 * else is left alone so the center tap that toggles the chrome still works. TalkBack gets one custom action per
 * marker, because a marker is not a link.
 */
@Composable
internal fun Modifier.noteTaps(
    text: AnnotatedString,
    surface: TextSurface,
    onNote: (NoteTap) -> Unit,
): Modifier {
    val markers = remember(text) { text.getStringAnnotations(NOTE_TAG, 0, text.length) }
    if (markers.isEmpty()) return this
    val latest = rememberUpdatedState(onNote)
    val actions =
        markers.map { marker ->
            CustomAccessibilityAction(stringResource(R.string.reader_footnote_marker, marker.item)) {
                surface.tapOf(marker.start, marker.end, marker.item)?.let(latest.value)
                true
            }
        }
    return this
        .onGloballyPositioned { surface.coordinates = it }
        .pointerInput(markers) {
            val side = Spacing.TouchTarget.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val boxes = surface.boxesOf(markers.map { Triple(it.start, it.end, it.item) })
                val hit = pickTarget(boxes, down.position.x, down.position.y, side) ?: return@awaitEachGesture
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                up.consume()
                surface.tapOf(hit, hit.value)?.let(latest.value)
            }
        }.semantics { customActions = actions }
}

private fun TextSurface.boxesOf(markers: List<Triple<Int, Int, String>>): List<TargetBox> {
    val result = layout ?: return emptyList()
    return markers.mapNotNull { (start, end, label) ->
        if (end <= start) return@mapNotNull null
        val r = result.getPathForRange(start, end).getBounds()
        TargetBox(r.left, r.top, r.right, r.bottom, TargetKind.NOTE, label)
    }
}

private fun TextSurface.tapOf(
    start: Int,
    end: Int,
    label: String,
): NoteTap? = boxesOf(listOf(Triple(start, end, label))).firstOrNull()?.let { tapOf(it, label) }

/** The marker in window px: text-local box moved by where the text sits in the window. */
private fun TextSurface.tapOf(
    box: TargetBox,
    label: String,
): NoteTap? {
    val at = coordinates?.takeIf { it.isAttached } ?: return null
    val topLeft = at.localToWindow(Offset(box.left, box.top))
    val bottomRight = at.localToWindow(Offset(box.right, box.bottom))
    return NoteTap(label, Rect(topLeft, bottomRight))
}
