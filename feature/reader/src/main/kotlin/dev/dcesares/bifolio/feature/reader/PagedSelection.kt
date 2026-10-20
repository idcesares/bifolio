package dev.dcesares.bifolio.feature.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.FoldSpec
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.graphics.Canvas as NativeCanvas

internal const val SELECTION_BAR_TAG = "selection-bar"
internal const val SELECTION_COPY_TAG = "selection-copy"
internal const val SELECTION_SHARE_TAG = "selection-share"
internal const val SELECTION_HANDLE_START_TAG = "selection-handle-start"
internal const val SELECTION_HANDLE_END_TAG = "selection-handle-end"

/** What the reader has selected on the paged surface (NAV-09); it belongs to one spread and one layout. */
internal class PagedSelection {
    var selection by mutableStateOf<TextSelection?>(null)

    fun clear() {
        selection = null
    }
}

/** Where the caret of a selection end is, in window px: the horizontal position and the line it sits on. */
internal class Caret(
    val x: Float,
    val top: Float,
    val bottom: Float,
)

/**
 * One text of a column as [SelectableRun], with its bounds in window px. [column] is the column of the spread it is
 * in; [text] keeps the platform layout the page is painted with, so the offsets match what is on screen.
 */
internal class PageRun(
    val source: ColumnText,
    val column: Int,
    private val originX: Float,
    private val originY: Float,
) : SelectableRun {
    private val layout = source.layout
    override val text: String = layout.text.toString()
    override val visibleStart: Int get() = source.visibleStart
    override val visibleEnd: Int get() = source.visibleEnd
    override val left: Float get() = originX + source.x
    override val right: Float get() = left + source.width
    override val top: Float get() = originY + source.y + source.clipTop
    override val bottom: Float get() = originY + source.y + source.clipBottom
    override val hidden: List<IntRange> get() = source.hidden

    override fun offsetAt(
        x: Float,
        y: Float,
    ): Int {
        val inLayout = (y - originY - source.y).coerceIn(source.clipTop, maxOf(source.clipTop, source.clipBottom - 1f))
        val line = layout.getLineForVertical(inLayout.toInt())
        val offset = layout.getOffsetForHorizontal(line, (x - left).coerceIn(0f, source.width))
        return offset.coerceIn(visibleStart, visibleEnd)
    }

    /** The caret of [offset]; an end that sits at the start of a wrapped line belongs to the line before it. */
    fun caret(
        offset: Int,
        isEnd: Boolean,
    ): Caret {
        val at = offset.coerceIn(visibleStart, visibleEnd)
        val own = layout.getLineForOffset(at)
        val wrapped = isEnd && own > 0 && layout.getLineStart(own) == at
        val line = if (wrapped) own - 1 else own
        val x = if (wrapped) layout.getLineRight(line) else layout.getPrimaryHorizontal(at)
        val lineTop = maxOf(layout.getLineTop(line).toFloat(), source.clipTop)
        val lineBottom = minOf(layout.getLineBottom(line).toFloat(), source.clipBottom)
        return Caret(left + x, originY + source.y + lineTop, originY + source.y + maxOf(lineBottom, lineTop))
    }
}

/** The texts of the shown spread in reading order, columns left to right, bounds in window px. */
internal fun pageRunsOf(
    layout: PagedLayout?,
    store: SpreadStore?,
    index: Int,
    originX: Float,
    originY: Float,
): List<PageRun> {
    if (layout == null || store == null || index < 0) return emptyList()
    val geometry = layout.geometry
    val runs = ArrayList<PageRun>()
    for ((column, page) in layout.flow.pagesOfSpread(index).withIndex()) {
        val prepared = store.columns[page] ?: continue
        val x = originX + geometry.columnLefts[column]
        val y = originY + geometry.verticalMargin
        for (source in prepared.texts) runs.add(PageRun(source, column, x, y))
    }
    return runs
}

/** Paints the selection as a translucent wash over the text of one column; it changes no layout (NAV-09). */
internal class SelectionWash(
    private val selection: TextSelection,
    private val runs: List<PageRun>,
    private val paint: Paint,
) {
    private val path = Path()

    /** [canvas] is already translated to the origin of [column]. */
    fun paint(
        canvas: NativeCanvas,
        column: Int,
    ) {
        for ((index, run) in runs.withIndex()) {
            if (run.column == column) paintRun(canvas, index, run)
        }
    }

    private fun paintRun(
        canvas: NativeCanvas,
        index: Int,
        run: PageRun,
    ) {
        val (from, to) = SelectionMath.rangeIn(selection, index, run) ?: return
        run.source.let { text ->
            path.rewind()
            text.layout.getSelectionPath(from, to, path)
            canvas.save()
            canvas.translate(text.x, text.y)
            canvas.clipRect(0f, text.clipTop, text.width, text.clipBottom)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}

/**
 * The drag handles and the Copy and Share bar over the paged surface (NAV-09). Everything is positioned from the
 * runs the page was painted with; nothing here is measured again or touches pagination. The bar and handles appear
 * and go without animation, so there is nothing to reduce (MOT-07).
 */
@Composable
internal fun PagedSelectionOverlay(
    state: PagedSelection,
    runs: () -> List<PageRun>,
    fold: FoldSpec?,
    windowWidth: Int,
    windowHeight: Int,
    modifier: Modifier = Modifier,
) {
    val selection = state.selection ?: return
    val all = runs()
    if (all.isEmpty() || selection.isEmpty) return
    val start = selection.start
    val end = selection.end
    val first = all.getOrNull(start.run)?.caret(start.offset, isEnd = false) ?: return
    val last = all.getOrNull(end.run)?.caret(end.offset, isEnd = true) ?: return
    Box(modifier.fillMaxSize()) {
        key("anchor") {
            SelectionHandleView(
                caret = if (selection.anchor == start) first else last,
                label =
                    stringResource(
                        if (selection.anchor ==
                            start
                        ) {
                            R.string.reader_selection_start_handle
                        } else {
                            R.string.reader_selection_end_handle
                        },
                    ),
                tag = if (selection.anchor == start) SELECTION_HANDLE_START_TAG else SELECTION_HANDLE_END_TAG,
                onMove = { point ->
                    val position = SelectionMath.positionAt(runs(), point.x, point.y, strict = false)
                    val current = state.selection
                    if (position != null && current != null) state.selection = TextSelection(position, current.focus)
                },
            )
        }
        key("focus") {
            SelectionHandleView(
                caret = if (selection.focus == end) last else first,
                label =
                    stringResource(
                        if (selection.focus ==
                            end
                        ) {
                            R.string.reader_selection_end_handle
                        } else {
                            R.string.reader_selection_start_handle
                        },
                    ),
                tag = if (selection.focus == end) SELECTION_HANDLE_END_TAG else SELECTION_HANDLE_START_TAG,
                onMove = { point ->
                    val position = SelectionMath.positionAt(runs(), point.x, point.y, strict = false)
                    val current = state.selection
                    if (position != null && current != null) state.selection = TextSelection(current.anchor, position)
                },
            )
        }
        val text = SelectionMath.textOf(all, selection)
        val region = selectionRegion(fold, first.x, windowWidth)
        val margin = with(LocalDensity.current) { Spacing.M.roundToPx() }
        val gap = with(LocalDensity.current) { Spacing.S.roundToPx() }
        val handleReach = with(LocalDensity.current) { Spacing.TouchTarget.roundToPx() }
        SelectionBarLayout(
            centerX = first.x.roundToInt(),
            top = first.top.roundToInt(),
            bottom = last.bottom.roundToInt() + handleReach,
            windowHeight = windowHeight,
            regionLeft = region.first,
            regionRight = region.last,
            margin = margin,
            gap = gap,
        ) {
            SelectionBar(text = text, onDone = state::clear)
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SelectionBarLayout(
    centerX: Int,
    top: Int,
    bottom: Int,
    windowHeight: Int,
    regionLeft: Int,
    regionRight: Int,
    margin: Int,
    gap: Int,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val room = (regionRight - regionLeft - margin * 2).coerceAtLeast(0)
        val placeable = measurables.first().measure(Constraints(maxWidth = room.coerceAtLeast(1)))
        val at =
            placeSelectionToolbar(
                centerX,
                top,
                bottom,
                placeable.width,
                placeable.height,
                windowHeight,
                regionLeft,
                regionRight,
                margin,
                gap,
            )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(at) }
    }
}

@Composable
private fun SelectionHandleView(
    caret: Caret,
    label: String,
    tag: String,
    onMove: (Offset) -> Unit,
) {
    val palette = BifolioTheme.palette
    val density = LocalDensity.current
    val half = with(density) { (Spacing.TouchTarget / 2).toPx() }
    val latestCaret by rememberUpdatedState(caret)
    val latestMove by rememberUpdatedState(onMove)
    Box(
        Modifier
            .offset { IntOffset((caret.x - half).roundToInt(), caret.bottom.roundToInt()) }
            .size(Spacing.TouchTarget)
            .testTag(tag)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                var point = Offset.Zero
                detectDragGestures(
                    onDragStart = {
                        val c = latestCaret
                        point = Offset(c.x, (c.top + c.bottom) / 2f)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        point += amount
                        latestMove(point)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stem = 2.dp.toPx()
            val radius = 7.dp.toPx()
            val cx = size.width / 2f
            val cy = 10.dp.toPx()
            val lineHeight = (caret.bottom - caret.top).coerceAtLeast(0f)
            drawLine(palette.accent, Offset(cx, -lineHeight), Offset(cx, cy), stem)
            drawCircle(palette.accent, radius, Offset(cx, cy))
        }
    }
}

@Composable
private fun SelectionBar(
    text: String,
    onDone: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.reader_selection_clip_label)
    val copied = stringResource(R.string.reader_selection_copied)
    val shareTitle = stringResource(R.string.reader_selection_share_title)
    val title = stringResource(R.string.reader_selection_title)
    val clear = stringResource(R.string.reader_selection_clear)
    val shape = RoundedCornerShape(Spacing.SheetRadius)
    Row(
        Modifier
            .clip(shape)
            .background(palette.page)
            .border(BorderStroke(Spacing.Rule, palette.rule), shape)
            .testTag(SELECTION_BAR_TAG)
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Polite
                customActions =
                    listOf(
                        CustomAccessibilityAction(clear) {
                            onDone()
                            true
                        },
                    )
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        BarAction(BifolioIcons.Copy, stringResource(R.string.reader_selection_copy), SELECTION_COPY_TAG) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, text))) }
            // Android 13 and later confirm a copy on their own.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
            onDone()
        }
        BarAction(BifolioIcons.Share2, stringResource(R.string.reader_selection_share), SELECTION_SHARE_TAG) {
            context.shareText(text, shareTitle)
            onDone()
        }
    }
}

@Composable
private fun BarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    val palette = BifolioTheme.palette
    Row(
        Modifier
            .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.M)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        BifolioIcon(icon, contentDescription = null, tint = palette.ink)
        BasicText(
            label,
            style = BifolioTheme.typography.caption.copy(color = palette.ink, fontWeight = FontWeight.Medium),
        )
    }
}

/** Offers [text] to other apps as plain text (NAV-09). Quietly does nothing when no app can take it. */
internal fun Context.shareText(
    text: String,
    title: String,
): Boolean {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, title)
    if (this !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
