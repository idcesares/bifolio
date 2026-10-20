package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec

internal const val TOC_SHEET_TAG = "toc-sheet"
internal const val TOC_ROW_TAG = "toc-row"

internal const val SHEET_MAX_HEIGHT = 0.85f
private const val MAX_INDENT_STEPS = 5
private const val ROWS_ABOVE_CURRENT = 2
internal val CurrentBar = 3.dp

/** Where the pane may not go: the sheet or pane keeps clear of a separating fold (DESIGN section 5). */
internal data class TocClearance(
    /** Space kept free at the start edge, up to and including a vertical fold. */
    val start: Dp = 0.dp,
    /** Space kept free at the top, down to and including a horizontal fold (tabletop). */
    val top: Dp = 0.dp,
)

/**
 * Clearance for a fold in window pixels: a separating vertical fold keeps the pane on its end side, a separating
 * horizontal one (tabletop) keeps it below. Folds that do not separate pages leave the whole window free.
 */
internal fun FoldSpec?.tocClearance(toDp: (Int) -> Dp): TocClearance =
    when {
        this == null || !isSeparating -> TocClearance()
        orientation == FoldOrientation.Vertical -> TocClearance(start = toDp(right.coerceAtLeast(0)))
        else -> TocClearance(top = toDp(bottom.coerceAtLeast(0)))
    }

/** What the sheet shows and does. [current] is an entry index of [contents], or null before the first heading. */
internal class TocState(
    val contents: TableOfContents,
    val current: Int?,
    val onSelect: (TocEntry) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Table of contents (UI-07): a bottom sheet on windows narrower than 840dp, a side pane at the end edge from
 * 840dp, decided from the window width only (LAY-01). The current section is marked; H1 to H3 are listed and
 * deeper headings open under their parent on request. Choosing an entry reports it through [TocState.onSelect];
 * the caller turns it into an anchor jump (ANC-06).
 *
 * Motion (MOT-06, MOT-07): scrim and panel come in on the chrome springs, the panel sliding from its edge; under
 * reduced motion both are a plain fade. Both can be interrupted: toggling [visible] retargets the transition.
 */
@Composable
internal fun TocSheet(
    state: TocState,
    visible: Boolean,
    modifier: Modifier = Modifier,
    clearance: TocClearance = TocClearance(),
) {
    val reduced = rememberReducedMotion()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = maxWidth >= Spacing.SidePaneMinWindow
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(BifolioMotion.fade()),
            exit = fadeOut(BifolioMotion.fade()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = Spacing.SCRIM_ALPHA))
                    .pointerInput(Unit) { detectTapGestures { state.onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier =
                Modifier
                    .align(if (side) Alignment.CenterEnd else Alignment.BottomCenter)
                    .padding(start = clearance.start, top = clearance.top),
            enter = panelEnter(side, reduced),
            exit = panelExit(side, reduced),
        ) {
            TocPanel(state, side, maxWidth - clearance.start, maxHeight - clearance.top, clearance.top > 0.dp)
        }
    }
}

internal fun panelEnter(
    side: Boolean,
    reduced: Boolean,
) = when {
    reduced -> fadeIn(BifolioMotion.fade())
    side -> fadeIn(BifolioMotion.fade()) + slideInHorizontally(BifolioMotion.chromeEnter()) { it }
    else -> fadeIn(BifolioMotion.fade()) + slideInVertically(BifolioMotion.chromeEnter()) { it }
}

internal fun panelExit(
    side: Boolean,
    reduced: Boolean,
) = when {
    reduced -> fadeOut(BifolioMotion.fade())
    side -> fadeOut(BifolioMotion.chromeExit()) + slideOutHorizontally(BifolioMotion.chromeExit()) { it }
    else -> fadeOut(BifolioMotion.chromeExit()) + slideOutVertically(BifolioMotion.chromeExit()) { it }
}

@Composable
private fun TocPanel(
    state: TocState,
    side: Boolean,
    availableWidth: Dp,
    availableHeight: Dp,
    belowFold: Boolean,
) {
    val palette = BifolioTheme.palette
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val shape =
        if (side) {
            RoundedCornerShape(topStart = Spacing.SheetRadius, bottomStart = Spacing.SheetRadius)
        } else {
            RoundedCornerShape(topStart = Spacing.SheetRadius, topEnd = Spacing.SheetRadius)
        }
    val sizing =
        if (side) {
            Modifier
                .width(Spacing.SidePaneWidth.coerceAtMost(availableWidth.coerceAtLeast(0.dp)))
                .fillMaxHeight()
        } else {
            // In tabletop the sheet fills the half below the fold; elsewhere it stops short of the page top.
            Modifier
                .widthIn(max = Spacing.SheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = if (belowFold) availableHeight else availableHeight * SHEET_MAX_HEIGHT)
        }
    Column(
        sizing
            .clip(shape)
            .background(palette.page)
            .testTag(TOC_SHEET_TAG)
            // Taps inside the panel must not reach the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = insets.calculateBottomPadding()),
    ) {
        TocHeader(state.onDismiss, Modifier.padding(top = if (side) insets.calculateTopPadding() else 0.dp))
        TocList(state, Modifier.weight(1f))
    }
}

@Composable
private fun TocHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.L, end = Spacing.S, top = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = stringResource(R.string.reader_toc_title),
            modifier = Modifier.weight(1f),
            style = BifolioTheme.typography.headings[1].copy(color = BifolioTheme.palette.ink),
        )
        BifolioIconButton(
            icon = BifolioIcons.ChevronDown,
            description = stringResource(R.string.reader_toc_close),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun TocList(
    state: TocState,
    modifier: Modifier = Modifier,
) {
    val contents = state.contents
    var expanded by remember(contents) { mutableStateOf(contents.initiallyExpanded(state.current)) }
    val rows = remember(contents, expanded) { contents.rows(expanded) }
    val highlighted = contents.highlightedRow(state.current, expanded)
    val firstShown = rows.indexOfFirst { it.index == highlighted }.coerceAtLeast(0)
    val listState = rememberLazyListState((firstShown - ROWS_ABOVE_CURRENT).coerceAtLeast(0))
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Spacing.S),
    ) {
        itemsIndexed(rows, key = { _, row -> row.index }) { _, row ->
            TocRowItem(
                row = row,
                isCurrent = row.index == highlighted,
                onSelect = { state.onSelect(row.entry) },
                onToggle = { expanded = if (row.index in expanded) expanded - row.index else expanded + row.index },
            )
        }
    }
}

@Composable
private fun TocRowItem(
    row: TocRow,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val currentLabel = stringResource(R.string.reader_toc_current)
    val bar = palette.accent
    val bold = isCurrent || row.depth == 0
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.TouchTarget)
            .testTag(TOC_ROW_TAG)
            .background(if (isCurrent) palette.codeSurface else Color.Transparent)
            .drawBehind {
                if (isCurrent) drawRect(bar, Offset.Zero, Size(CurrentBar.toPx(), size.height))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = row.entry.title,
            modifier =
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = Spacing.TouchTarget)
                    .semantics(mergeDescendants = true) {
                        selected = isCurrent
                        if (isCurrent) stateDescription = currentLabel
                    }.clickable(role = Role.Button, onClick = onSelect)
                    .padding(
                        start = Spacing.L + Spacing.L * row.depth.coerceAtMost(MAX_INDENT_STEPS),
                        end = Spacing.S,
                        top = Spacing.M,
                        bottom = Spacing.M,
                    ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style =
                type.body.copy(
                    color =
                        when {
                            isCurrent -> palette.accent
                            row.depth == 0 -> palette.ink
                            else -> palette.inkMuted
                        },
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
        if (row.deeper > 0) {
            val action =
                pluralStringResource(
                    if (row.expanded) R.plurals.reader_toc_hide_deeper else R.plurals.reader_toc_show_deeper,
                    row.deeper,
                    row.deeper,
                )
            Box(
                Modifier
                    .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = action },
                contentAlignment = Alignment.Center,
            ) {
                BifolioIcon(
                    if (row.expanded) BifolioIcons.ChevronDown else BifolioIcons.ChevronRight,
                    contentDescription = null,
                    tint = palette.inkMuted,
                )
            }
        }
    }
}
