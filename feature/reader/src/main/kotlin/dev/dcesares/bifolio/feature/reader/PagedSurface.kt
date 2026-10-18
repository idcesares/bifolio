package dev.dcesares.bifolio.feature.reader

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import dev.dcesares.bifolio.layout.AnchorMapper
import dev.dcesares.bifolio.layout.Page
import dev.dcesares.bifolio.layout.PageFlow
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.text.TextLayoutFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Taps in the outer 30% on each side turn the page; the middle 40% toggles the chrome (NAV-02). */
private const val TAP_ZONE = 0.30f
private const val OPAQUE = 255
private const val PREFETCH_BEHIND = 1
private const val PREFETCH_AHEAD = 2
private const val RETAIN_BEHIND = 2
private const val RETAIN_AHEAD = 3

/**
 * Column ops prepared off the main thread, shared by every snapshot of one request. Keyed by [Page]: a page
 * is its slices, so the same page in a provisional and in the canonical flow is prepared once.
 */
internal class SpreadStore(
    val painter: ColumnPainter,
) {
    val columns: SnapshotStateMap<Page, PreparedColumn> = mutableStateMapOf()

    /** Prepares spreads around [index] in reading order of need, then drops the ones far away. */
    suspend fun prime(
        layout: PagedLayout,
        index: Int,
    ) {
        val flow = layout.flow
        withContext(Dispatchers.Default) {
            val order = listOf(index) + (1..PREFETCH_AHEAD).map { index + it } + (1..PREFETCH_BEHIND).map { index - it }
            for (spread in order) {
                if (spread !in 0 until flow.spreadCount) continue
                for ((column, page) in flow.pagesOfSpread(spread).withIndex()) {
                    ensureActive()
                    if (columns.containsKey(page)) continue
                    val draw = layout.detailer.detail(page, column)
                    columns[page] = painter.prepare(draw, layout.geometry.columnHeight)
                }
            }
            val keep = HashSet<Page>()
            for (spread in (index - RETAIN_BEHIND)..(index + RETAIN_AHEAD)) {
                if (spread in 0 until flow.spreadCount) keep.addAll(flow.pagesOfSpread(spread))
            }
            columns.keys.filter { it !in keep }.forEach { columns.remove(it) }
        }
    }
}

/**
 * Paged reading surface (SCO-03). Draws prepared spreads on one canvas: pagination and line breaking happened
 * off the main thread, so turning a page only paints (ADR-0013). [initialAnchor] is where reading resumes;
 * [onSettled] reports the first anchor of the spread the reader stopped on (ANC-02).
 *
 * In tabletop (POS-04) pages are painted and hit-tested only inside the text region, and the control deck fills
 * the deck region of the same surface. The deck seeks through [document] by anchor, never by page number.
 * [onOpenToc] is null until the table of contents exists (M5), which leaves its button disabled.
 */
@Composable
internal fun PagedSurface(
    layout: PagedLayout?,
    initialAnchor: Anchor,
    onRequest: (PageRequest) -> Unit,
    onSettled: (Anchor) -> Unit,
    onFirstDrawn: () -> Unit,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
    notices: Set<ReaderNotice> = emptySet(),
    foldSource: FoldSource = FoldSource.None,
    requestDebounceMillis: Long = REQUEST_DEBOUNCE_MILLIS,
    document: Document? = null,
    onOpenToc: (() -> Unit)? = null,
    volumeKeysTurn: Boolean = false,
    jumps: Flow<Anchor> = emptyFlow(),
    appearance: AppearanceProfile = AppearanceProfile(),
) {
    val fold by foldSource.fold.collectAsStateWithLifecycle(initialValue = foldSource.initialFold)
    val palette = BifolioTheme.palette
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = ReaderInsets.asPaddingValues()
    val bodySp = BifolioTheme.typography.body.fontSize.value
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val labels = CalloutLabels(CalloutKind.entries.associateWith { calloutLabel(it) })
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val turn = remember { PageTurnController(scope) }
    val continuity = remember { ContinuityMotion(scope) }
    val tableScroll = remember { mutableStateMapOf<Int, Int>() }
    var shown by remember { mutableStateOf(initialAnchor) }
    var noticesDismissed by rememberSaveable { mutableStateOf(false) }

    val flow = layout?.flow
    val index = remember(flow, shown) { flow?.spreadIndexOf(shown) ?: -1 }
    val store =
        remember(layout?.request, palette, labels) {
            layout?.let {
                val metrics = it.geometry.metrics
                val painter =
                    ColumnPainter(
                        palette = palette,
                        density = it.request.density,
                        bodyPx = it.request.bodyPx,
                        cellPadding = metrics.tableCellPadding.toFloat(),
                        factory = TextLayoutFactory(it.config),
                        labels = labels,
                    )
                SpreadStore(painter)
            }
        }

    val turnStyle = turnStyleOf(appearance.turn)
    turn.style = if (reduced && turnStyle == PageTurnStyle.SLIDE) PageTurnStyle.FADE else turnStyle
    SideEffect {
        turn.canTurn = { dir -> flow != null && (index - dir) in 0 until flow.spreadCount }
        turn.commit = { dir ->
            // ANC-01: the settled anchor is the first visible character of the leading column of the spread.
            val settled = flow?.let { AnchorMapper.leftmostVisibleAnchor(it, index - dir) }
            if (settled != null) {
                continuity.cancel()
                shown = settled
                onSettled(settled)
            }
        }
    }
    LaunchedEffect(layout, index, store) {
        if (layout != null && store != null && index >= 0) store.prime(layout, index)
    }
    // ANC-06: the return chip asks for an anchor to be shown again, exactly, not rounded to a spread start.
    LaunchedEffect(jumps) {
        jumps.collect { target ->
            turn.reset()
            continuity.cancel()
            shown = target
            onSettled(target)
        }
    }

    val insetStart = insets.calculateStartPadding(direction)
    val insetEnd = insets.calculateEndPadding(direction)
    val insetLeft = insets.calculateLeftPadding(direction)
    val insetTop = insets.calculateTopPadding()
    val insetBottom = insets.calculateBottomPadding()

    // NAV-03, NAV-04: keys act on the surface, which asks for focus so they arrive without a tap first.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val keys = rememberUpdatedState(volumeKeysTurn)
    val assets = LocalContext.current.assets
    val fonts = remember(appearance.font, assets) { fontSetOf(appearance.font, assets) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(palette.page)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val command = event.readerCommand(keys.value)
                if (command == null) {
                    false
                } else {
                    val handled = flow != null && command.isHandledBySpreads(onOpenToc)
                    if (handled && flow != null) {
                        turn.grab().let { if (it != 0) turn.commitNow(it) }
                        when (command) {
                            ReaderCommand.NEXT -> turn.turn(-1)
                            ReaderCommand.PREVIOUS -> turn.turn(1)
                            ReaderCommand.TOC -> onOpenToc?.invoke()
                            else -> {
                                val target = if (command == ReaderCommand.FIRST) 0 else flow.spreadCount - 1
                                AnchorMapper.leftmostVisibleAnchor(flow, target)?.let { settled ->
                                    continuity.cancel()
                                    shown = settled
                                    onSettled(settled)
                                }
                            }
                        }
                    }
                    handled
                }
            }.focusable(),
    ) {
        val startPx = with(density) { insetStart.toPx() }
        // Platform fold bounds are physical, so the fold shifts by the left inset, not the start inset (RTL).
        val leftPx = with(density) { insetLeft.toPx() }
        val endPx = with(density) { insetEnd.toPx() }
        val topPx = with(density) { insetTop.toPx() }
        val bottomPx = with(density) { insetBottom.toPx() }
        val usableWidth = (constraints.maxWidth - startPx - endPx).toInt().coerceAtLeast(1)
        val usableHeight = (constraints.maxHeight - topPx - bottomPx).toInt().coerceAtLeast(1)
        val usableFold = fold?.translatedBy(leftPx.toInt(), topPx.toInt())
        val request =
            PageRequest(
                usableWidth,
                usableHeight,
                density.density,
                density.fontScale,
                bodySp,
                language,
                usableFold,
                lineHeight = appearance.lineHeight,
                marginScale = appearance.marginScale,
                forcedColumns = appearance.columns,
                hyphenate = appearance.hyphenate,
                justify = appearance.justify,
                fonts = fonts,
            )
        val latestRequest by rememberUpdatedState(request)
        val latestOnRequest by rememberUpdatedState(onRequest)
        // The first request starts at once; a burst (window drag, hinge sweep) collapses to its last request.
        LaunchedEffect(requestDebounceMillis) {
            snapshotFlow { latestRequest }
                .debounceAfterFirst(requestDebounceMillis)
                .collect { latestOnRequest(it) }
        }
        // MOT-05: once a layout for new geometry has its spread ready, the anchor line glides to its new place.
        LaunchedEffect(layout, shown, store, startPx, topPx) {
            if (layout == null || store == null) return@LaunchedEffect
            continuity.track(layout, shown, startPx, topPx, reduced) {
                val pages = layout.flow.pagesOfSpread(layout.flow.spreadIndexOf(shown).coerceAtLeast(0))
                snapshotFlow { pages.all { store.columns.containsKey(it) } }.first { it }
            }
        }
        turn.width = constraints.maxWidth.toFloat()

        val env = remember { PagedGesture() }
        env.turn = turn
        env.continuity = continuity
        env.width = constraints.maxWidth.toFloat()
        env.onToggleChrome = onToggleChrome
        env.tables = { position ->
            tableAt(layout, store, index, position, startPx, topPx)
        }
        env.tableScroll = tableScroll

        val drawn = remember(layout?.request) { BooleanArray(1) }
        val pagePaint = remember(palette) { Paint().apply { color = palette.page.toArgb() } }
        val glowPaint = remember(palette) { Paint().apply { color = palette.accent.toArgb() } }
        val deckRegion = layout?.geometry?.deckRegion
        val pageBox =
            if (deckRegion == null) {
                Modifier.fillMaxSize()
            } else {
                // POS-04: the page owns the text region only, so nothing is painted or tapped in the deck.
                val textBottom = topPx + checkNotNull(layout).geometry.textRegion.bottom
                Modifier.fillMaxWidth().height(with(density) { textBottom.toDp() }).clipToBounds()
            }
        Box(
            pageBox
                .pointerInput(Unit) { awaitEachGesture { readGesture(env) } }
                .drawBehind {
                    drawIntoCanvas { c ->
                        val spreads =
                            SpreadFrame(
                                layout,
                                store,
                                index,
                                turn,
                                startPx,
                                topPx,
                                size.width,
                                size.height,
                                FramePaints(pagePaint, glowPaint, continuity),
                            )
                        val painted = spreads.paint(c.nativeCanvas) { block -> tableScroll[block] ?: 0 }
                        if (painted && !drawn[0]) {
                            drawn[0] = true
                            onFirstDrawn()
                        }
                    }
                },
        )
        if (layout != null && deckRegion != null) {
            val seek = remember { DeckSeek() }
            seek.document = document
            seek.flow = layout.flow
            seek.turn = turn
            seek.shown = { shown }
            seek.show = {
                continuity.cancel()
                shown = it
            }
            seek.settled = onSettled
            val progress = seek.scrubbing ?: seek.progress()
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (leftPx + deckRegion.left).roundToInt(),
                            (topPx + deckRegion.top).roundToInt(),
                        )
                    }.size(with(density) { deckRegion.width.toDp() }, with(density) { deckRegion.height.toDp() })
                    .testTag(DECK_TAG),
            ) {
                ControlDeck(
                    progress = progress,
                    onPrevious = { turn.turnFromInput(1) },
                    onNext = { turn.turnFromInput(-1) },
                    onSeek = seek::seekTo,
                    onSeekFinished = seek::finish,
                    onOpenToc = onOpenToc,
                )
            }
        }
        if (notices.isNotEmpty() && !noticesDismissed) {
            NoticeStack(
                notices = notices,
                onDismiss = { noticesDismissed = true },
                modifier = Modifier.padding(start = insetStart + Spacing.L, end = insetEnd + Spacing.L, top = insetTop),
            )
        }
    }
}

/** Page background, anchor highlight paint and the continuity motion that drives it (MOT-05). */
private class FramePaints(
    val page: Paint,
    val glow: Paint,
    val continuity: ContinuityMotion,
)

/** What one frame needs to paint: the shown spread and, during a turn, the one coming in. */
private class SpreadFrame(
    private val layout: PagedLayout?,
    private val store: SpreadStore?,
    private val index: Int,
    private val turn: PageTurnController,
    private val originX: Float,
    private val originY: Float,
    private val width: Float,
    private val height: Float,
    private val paints: FramePaints,
) {
    /** Returns true when the shown spread was complete. */
    fun paint(
        canvas: Canvas,
        tableScroll: (Int) -> Int,
    ): Boolean {
        if (layout == null || store == null || index < 0) return false
        val p = turn.progress
        val dir =
            if (p < 0f) {
                -1
            } else if (p > 0f) {
                1
            } else {
                0
            }
        val incoming = index - dir
        val slide = turn.style == PageTurnStyle.SLIDE
        val fade = turn.style == PageTurnStyle.FADE
        if (dir != 0 && incoming in 0 until layout.flow.spreadCount) {
            if (slide) {
                val lead = -dir * BifolioMotion.PARALLAX_FRACTION * width * (1f - abs(p))
                spread(canvas, incoming, lead, OPAQUE, tableScroll)
                spread(canvas, index, p * width, OPAQUE, tableScroll)
            } else if (fade) {
                spread(canvas, index, 0f, OPAQUE, tableScroll)
                spread(canvas, incoming, 0f, (abs(p) * OPAQUE).toInt(), tableScroll)
            }
        } else {
            canvas.save()
            canvas.translate(paints.continuity.offsetX, paints.continuity.offsetY)
            spread(canvas, index, 0f, OPAQUE, tableScroll)
            canvas.restore()
        }
        return layout.flow.pagesOfSpread(index).all { store.columns.containsKey(it) }
    }

    /** MOT-05: the soft highlight on the anchor line, under the text so the words stay legible. */
    private fun anchorGlow(canvas: Canvas) {
        val spot = paints.continuity.spot
        val alpha = paints.continuity.glow
        if (spot == null || alpha <= 0f || turn.progress != 0f) return
        if (spot.request != layout?.request) return
        paints.glow.alpha = (alpha * OPAQUE).roundToInt().coerceIn(0, OPAQUE)
        canvas.drawRect(spot.x, spot.y, spot.x + spot.width, spot.y + spot.height, paints.glow)
    }

    private fun spread(
        canvas: Canvas,
        spread: Int,
        dx: Float,
        alpha: Int,
        tableScroll: (Int) -> Int,
    ) {
        val flow = layout!!.flow
        val geometry = layout.geometry
        val layer = alpha < OPAQUE
        if (layer) canvas.saveLayerAlpha(0f, 0f, width, height, alpha)
        canvas.save()
        canvas.translate(dx, 0f)
        canvas.drawRect(0f, 0f, width, height, paints.page)
        if (spread == index && dx == 0f) anchorGlow(canvas)
        for ((column, page) in flow.pagesOfSpread(spread).withIndex()) {
            val prepared = store!!.columns[page] ?: continue
            canvas.save()
            canvas.translate(originX + geometry.columnLefts[column], originY + geometry.verticalMargin)
            for (op in prepared.ops) op.draw(canvas, tableScroll)
            canvas.restore()
        }
        canvas.restore()
        if (layer) canvas.restore()
    }
}

/** Where a drag that starts on a scrollable table lands (PAG-06). Window coordinates, so no insets to add. */
private class TableTarget(
    val blockIndex: Int,
    val maxScroll: Int,
)

private fun tableAt(
    layout: PagedLayout?,
    store: SpreadStore?,
    index: Int,
    position: Offset,
    originX: Float,
    originY: Float,
): TableTarget? {
    if (layout == null || store == null || index < 0) return null
    val geometry = layout.geometry
    for ((column, page) in layout.flow.pagesOfSpread(index).withIndex()) {
        val prepared = store.columns[page] ?: continue
        for (hit in prepared.tables) {
            val x = originX + geometry.columnLefts[column] + hit.x
            val y = originY + geometry.verticalMargin + hit.y
            if (position.x in x..(x + hit.width) && position.y in y..(y + hit.height)) {
                return TableTarget(hit.blockIndex, hit.maxScroll)
            }
        }
    }
    return null
}

/** A deck zone asked for a turn: finish a turn still running, then turn like a tap in the outer zone does. */
private fun PageTurnController.turnFromInput(dir: Int) {
    val heading = grab()
    if (heading != 0) commitNow(heading) else reset()
    turn(dir)
}

/**
 * Seeking from the deck scrubber (POS-04). A fraction of the source text becomes an anchor through the document,
 * never a page number (ANC-01). While the finger moves the spread under it follows without saving anything; on
 * release the spread snaps to its first anchor and is reported once, like a turn (ANC-02).
 */
private class DeckSeek {
    var document: Document? = null
    var flow: PageFlow? = null
    lateinit var turn: PageTurnController
    var shown: () -> Anchor = { Anchor(0, 0) }
    var show: (Anchor) -> Unit = {}
    var settled: (Anchor) -> Unit = {}

    /** The fraction under the finger, or null when the scrubber is idle. */
    var scrubbing by mutableStateOf<Float?>(null)
        private set

    fun progress(): Float {
        val doc = document ?: return 0f
        if (doc.source.isEmpty()) return 0f
        return (doc.sourceOffsetOf(shown()).toFloat() / doc.source.length).coerceIn(0f, 1f)
    }

    fun seekTo(fraction: Float) {
        val doc = document ?: return
        scrubbing = fraction
        turn.grab()
        turn.reset()
        show(doc.anchorAt((fraction * doc.source.length).toInt()))
    }

    fun finish() {
        scrubbing = null
        val current = flow ?: return
        val target = shown()
        val spread = current.spreadIndexOf(target)
        // A target past the pages paginated so far lands on the last page of a growing flow: keep the exact
        // anchor then, so the spread it belongs to appears once the pagination catches up.
        val beyond = !current.complete && spread == current.spreadCount - 1
        val settled = if (beyond) target else AnchorMapper.leftmostVisibleAnchor(current, spread) ?: target
        show(settled)
        this.settled(settled)
    }
}

/** Mutable bridge between the composition and the pointer input coroutine, which outlives recompositions. */
private class PagedGesture {
    lateinit var turn: PageTurnController
    lateinit var continuity: ContinuityMotion
    var width: Float = 1f
    var onToggleChrome: () -> Unit = {}
    var tables: (Offset) -> TableTarget? = { null }
    lateinit var tableScroll: SnapshotStateMap<Int, Int>

    fun tap(
        position: Offset,
        heading: Int,
    ) {
        if (heading != 0) turn.commitNow(heading) else turn.reset()
        val x = position.x / width
        when {
            x < TAP_ZONE -> turn.turn(1)
            x > 1f - TAP_ZONE -> turn.turn(-1)
            else -> onToggleChrome()
        }
    }

    fun canScroll(
        table: TableTarget,
        dx: Float,
    ): Boolean {
        val at = tableScroll[table.blockIndex] ?: 0
        return (dx < 0f && at < table.maxScroll) || (dx > 0f && at > 0)
    }

    fun scroll(
        table: TableTarget,
        dx: Float,
    ) {
        val at = tableScroll[table.blockIndex] ?: 0
        tableScroll[table.blockIndex] = (at - dx.toInt()).coerceIn(0, table.maxScroll)
    }
}

/** Where one touch has got to. */
private class TouchProgress(
    val downPosition: Offset,
    val heading: Int,
    val table: TableTarget?,
) {
    var total = Offset.Zero
    var dragging = false
    var scrollingTable = false
}

private suspend fun AwaitPointerEventScope.readGesture(env: PagedGesture) {
    val down = awaitFirstDown(requireUnconsumed = false)
    env.continuity.cancel()
    val touch = TouchProgress(down.position, env.turn.grab(), env.tables(down.position))
    val slop = viewConfiguration.touchSlop
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || change.isConsumed) return
        tracker.addPosition(change.uptimeMillis, change.position)
        if (change.changedToUpIgnoreConsumed()) {
            env.finish(touch, tracker.calculateVelocity().x, slop)
            return
        }
        if (!env.move(touch, change, slop)) return
    }
}

private fun PagedGesture.finish(
    touch: TouchProgress,
    velocity: Float,
    slop: Float,
) {
    when {
        touch.dragging && !touch.scrollingTable -> turn.release(velocity, touch.total.x)
        !touch.dragging && touch.total.getDistance() <= slop -> tap(touch.downPosition, touch.heading)
    }
}

/** Applies a pointer move. Returns false when the gesture is vertical and not ours. */
private fun PagedGesture.move(
    touch: TouchProgress,
    change: PointerInputChange,
    slop: Float,
): Boolean {
    val delta = change.positionChange()
    touch.total += delta
    val table = touch.table
    if (!touch.dragging && abs(touch.total.x) > slop && abs(touch.total.x) > abs(touch.total.y)) {
        touch.dragging = true
        touch.scrollingTable = table != null && canScroll(table, touch.total.x)
    }
    if (!touch.dragging) return abs(touch.total.y) <= slop
    change.consume()
    if (touch.scrollingTable && table != null) scroll(table, delta.x) else turn.drag(delta.x)
    return true
}

/** Spreads act on turns and jumps; the table of contents only when it exists; search and close are not ours yet. */
private fun ReaderCommand.isHandledBySpreads(onOpenToc: (() -> Unit)?): Boolean =
    when (this) {
        ReaderCommand.NEXT, ReaderCommand.PREVIOUS, ReaderCommand.FIRST, ReaderCommand.LAST -> true
        ReaderCommand.TOC -> onOpenToc != null
        ReaderCommand.SEARCH, ReaderCommand.CLOSE -> false
    }
