package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioButton
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import kotlin.math.roundToInt

internal const val NOTE_CARD_TAG = "footnote-card"
internal const val NOTE_GO_TO_TAG = "footnote-go-to"
internal const val NOTE_CLOSE_TAG = "footnote-close"

/** The card is at most this share of the window height; longer notes scroll inside it (UI-09). */
internal const val NOTE_MAX_HEIGHT = 0.4f

/** What the footnote card needs to show and do. */
internal class NoteCard(
    val tap: NoteTap,
    val definition: NoteDefinition,
    /** Show the definition (anchor jump with the return chip, ANC-06). */
    val onGoTo: () -> Unit,
    val onDismiss: () -> Unit,
    /** Links and markers inside the note behave as they do on the page. */
    val onLink: (String) -> Unit,
    val onNote: (NoteTap) -> Unit,
)

/**
 * Footnote card (UI-09). On a window narrower than 840dp it is a bottom sheet with a scrim; from 840dp it is a
 * popover next to the marker, placed below it or above when there is no room, inside the half of the window the
 * marker is in when a fold separates two halves, so it never straddles the fold (LAY-12). Either way the note is
 * shown as the rich text of its blocks, scrolls inside 40% of the window height, and offers "Go to note" and a close
 * control. The choice comes from the window width only (LAY-01).
 *
 * Motion (MOT-06, MOT-07): the sheet comes in on the chrome springs, the popover fades; under reduced motion both
 * are a plain fade. Toggling [card] retargets the transition, so it can be interrupted. Escape and the area outside
 * the card dismiss it.
 */
@Composable
internal fun FootnotePopover(
    card: NoteCard?,
    modifier: Modifier = Modifier,
    fold: FoldSpec? = null,
) {
    // The card stays on screen while it fades out, so keep the last one to draw.
    val held = remember { arrayOfNulls<NoteCard>(1) }
    if (card != null) held[0] = card
    val shown = card ?: held[0]
    val visible = card != null
    val reduced = rememberReducedMotion()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val tabletop = fold != null && fold.isSeparating && fold.orientation == FoldOrientation.Horizontal
        val wide = isSidePane(maxWidth) && !tabletop
        if (visible) {
            val scrim = if (wide) Modifier else Modifier.background(Color.Black.copy(alpha = Spacing.SCRIM_ALPHA))
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        scrim,
                    ).pointerInput(Unit) { detectTapGestures { shown?.onDismiss?.let { it() } } },
            )
        }
        if (shown == null) return@BoxWithConstraints
        if (wide) {
            AnchoredBox(shown.tap.bounds, fold) {
                AnimatedVisibility(
                    visible,
                    enter = fadeIn(BifolioMotion.fade()),
                    exit = fadeOut(BifolioMotion.fade()),
                ) {
                    NoteCardContent(shown, maxHeight * NOTE_MAX_HEIGHT, popover = true)
                }
            }
        } else {
            val clearance = with(LocalDensity.current) { fold.tocClearance { it.toDp() } }
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = clearance.start, top = clearance.top),
                enter = panelEnter(false, reduced),
                exit = panelExit(false, reduced),
            ) {
                val available = maxHeight - clearance.top
                NoteCardContent(
                    shown,
                    if (clearance.top > 0.dp) available else maxHeight * NOTE_MAX_HEIGHT,
                    popover = false,
                )
            }
        }
    }
}

/**
 * Places its single child next to [anchor] (px from the top-left): centered on it horizontally, below it or above
 * it, kept inside the window and, when a vertical fold separates two halves, inside the half the anchor is in.
 */
@Composable
private fun AnchoredBox(
    anchor: Rect,
    fold: FoldSpec?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val width = with(density) { Spacing.SidePaneWidth.roundToPx() }
    val gap = with(density) { Spacing.Xs.roundToPx() }
    val margin = with(density) { Spacing.L.roundToPx() }
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val total = constraints.maxWidth
        val high = constraints.maxHeight
        val split = fold != null && fold.isSeparating && fold.orientation == FoldOrientation.Vertical
        val first = split && fold != null && anchor.center.x <= (fold.left + fold.right) / 2f
        val regionLeft = if (split && !first) fold.right else 0
        val regionRight = if (split && first) fold.left else total
        val inner = (regionRight - regionLeft - 2 * margin).coerceAtLeast(0)
        val placeable =
            measurables.single().measure(
                Constraints(maxWidth = minOf(width, inner), maxHeight = (high * NOTE_MAX_HEIGHT).roundToInt()),
            )
        val at = placeBeside(anchor, placeable.width, placeable.height, high, regionLeft, regionRight, margin, gap)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(at.x, at.y) }
    }
}

@Composable
private fun NoteCardContent(
    card: NoteCard,
    maxHeight: Dp,
    popover: Boolean,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val title = stringResource(R.string.reader_footnote_title, card.tap.label)
    val shape =
        if (popover) {
            RoundedCornerShape(Spacing.SheetRadius)
        } else {
            RoundedCornerShape(topStart = Spacing.SheetRadius, topEnd = Spacing.SheetRadius)
        }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val sizing = if (popover) Modifier else Modifier.widthIn(max = Spacing.SheetMaxWidth).fillMaxWidth()
    val frame = Modifier.border(BorderStroke(Spacing.Rule, palette.rule), shape)
    Column(
        sizing
            .heightIn(max = maxHeight)
            .then(frame)
            .clip(shape)
            .background(palette.page)
            .testTag(NOTE_CARD_TAG)
            .semantics { paneTitle = title }
            // Escape closes, also when the focus is inside the card.
            .onPreviewKeyEvent { event ->
                (event.readerCommand(false) == ReaderCommand.CLOSE).also { if (it) card.onDismiss() }
            }.focusRequester(focus)
            .focusable()
            // Taps inside the card must not reach the dismissing area behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = if (popover) 0.dp else insets.calculateBottomPadding()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.L, end = Spacing.S, top = Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = title,
                modifier = Modifier.weight(1f),
                style = type.body.copy(color = palette.inkMuted, fontWeight = FontWeight.SemiBold),
            )
            BifolioIconButton(
                icon = if (popover) BifolioIcons.X else BifolioIcons.ChevronDown,
                description = stringResource(R.string.reader_footnote_close),
                onClick = card.onDismiss,
                modifier = Modifier.testTag(NOTE_CLOSE_TAG),
            )
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(paragraphGap()),
        ) {
            CompositionLocalProvider(
                LocalLinkHandler provides card.onLink,
                LocalNoteHandler provides card.onNote,
                LocalSearchHighlighter provides null,
            ) {
                BlockContent(card.definition.block)
            }
        }
        Box(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S)) {
            BifolioButton(
                text = stringResource(R.string.reader_footnote_go_to),
                onClick = card.onGoTo,
                icon = BifolioIcons.ArrowRight,
                modifier = Modifier.testTag(NOTE_GO_TO_TAG),
            )
        }
    }
}

/**
 * Where a card of [width] x [height] goes next to [anchor]: centered on it and kept inside `[regionLeft, regionRight]`
 * (the half of a fold the anchor is in, or the window), below it, or above it when below does not fit, or at the
 * bottom edge of a window of [windowHeight] when neither does. [margin] keeps it off the edges, [gap] off the anchor.
 */
internal fun placeBeside(
    anchor: Rect,
    width: Int,
    height: Int,
    windowHeight: Int,
    regionLeft: Int,
    regionRight: Int,
    margin: Int,
    gap: Int,
): IntOffset {
    val low = regionLeft + margin
    val x = (anchor.center.x - width / 2f).roundToInt().coerceIn(low, maxOf(low, regionRight - margin - width))
    val below = (anchor.bottom + gap).roundToInt()
    val above = (anchor.top - gap).roundToInt() - height
    val y =
        when {
            below + height <= windowHeight - margin -> below
            above >= margin -> above
            else -> (windowHeight - margin - height).coerceAtLeast(margin)
        }
    return IntOffset(x, y)
}
