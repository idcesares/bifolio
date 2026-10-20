package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import kotlin.math.roundToInt

internal const val DECK_TAG = "control-deck"
internal const val DECK_PREVIOUS_TAG = "deck-previous"
internal const val DECK_NEXT_TAG = "deck-next"
internal const val DECK_SCRUBBER_TAG = "deck-scrubber"
internal const val DECK_TOC_TAG = "deck-toc"
internal const val DECK_SEARCH_TAG = "deck-search"

private val ThumbRadius = 7.dp
private const val DISABLED_ALPHA = 0.4f

/**
 * Control deck of the tabletop posture (POS-04, NAV-02): previous and next zones, a progress scrubber and a
 * table of contents button. It fills the deck region it is placed in, so it works from 2 x 48dp upwards: the
 * zone row takes whatever height the scrubber row leaves. The deck holds controls only, never text, and has no
 * motion of its own, so there is nothing to reduce (MOT-07).
 *
 * [progress] is the reading progress, 0 to 1. The scrubber reports a fraction through [onSeek] while it is
 * dragged and calls [onSeekFinished] when the finger leaves; the caller turns the fraction into an anchor.
 *
 * The table of contents button is shown only when the document has headings ([showToc], UI-07). While [onOpenToc]
 * is null it is shown disabled and opens nothing. A search button joins it when [onOpenSearch] is given (UI-08).
 */
@Composable
internal fun ControlDeck(
    progress: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenToc: (() -> Unit)? = null,
    showToc: Boolean = true,
    onOpenSearch: (() -> Unit)? = null,
) {
    val palette = BifolioTheme.palette
    Column(modifier.fillMaxSize().background(palette.page)) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            DeckZone(
                icon = BifolioIcons.ChevronLeft,
                description = stringResource(R.string.reader_deck_previous),
                onClick = onPrevious,
                tag = DECK_PREVIOUS_TAG,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.fillMaxHeight().width(Spacing.Rule).background(palette.rule))
            DeckZone(
                icon = BifolioIcons.ChevronRight,
                description = stringResource(R.string.reader_deck_next),
                onClick = onNext,
                tag = DECK_NEXT_TAG,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().height(Spacing.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckScrubber(progress, onSeek, onSeekFinished, Modifier.weight(1f))
            if (onOpenSearch != null) SearchDeckButton(onOpenSearch)
            if (showToc) TocButton(onOpenToc)
        }
    }
}

@Composable
private fun DeckZone(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            .defaultMinSize(minHeight = Spacing.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BifolioIcon(
            icon,
            contentDescription = description,
            tint = BifolioTheme.palette.inkMuted,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** Opens search (UI-08). */
@Composable
private fun SearchDeckButton(onOpen: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
            .clickable(role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        BifolioIcon(
            BifolioIcons.Search,
            contentDescription = stringResource(R.string.reader_search_open),
            tint = BifolioTheme.palette.inkMuted,
            modifier = Modifier.testTag(DECK_SEARCH_TAG),
        )
    }
}

/** Opens the table of contents (UI-07). Without [onOpen] (previews and harnesses) the button is disabled. */
@Composable
private fun TocButton(onOpen: (() -> Unit)?) {
    val enabled = onOpen != null
    Box(
        Modifier
            .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(
                if (onOpen != null) {
                    Modifier.clickable(role = Role.Button, onClick = onOpen)
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        role = Role.Button
                        disabled()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BifolioIcon(
            BifolioIcons.List,
            contentDescription = stringResource(R.string.reader_deck_toc),
            tint = BifolioTheme.palette.inkMuted,
            modifier = Modifier.testTag(DECK_TOC_TAG),
        )
    }
}

/**
 * Horizontal progress track at least 48dp tall. Touch and drag report a fraction; accessibility gets a progress
 * bar with a set-progress action, so a screen reader or a keyboard can seek too.
 */
@Composable
private fun DeckScrubber(
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val latestSeek by rememberUpdatedState(onSeek)
    val latestFinished by rememberUpdatedState(onSeekFinished)
    val shown = progress.coerceIn(0f, 1f)
    val label = stringResource(R.string.reader_deck_progress)
    val value = stringResource(R.string.reader_deck_progress_value, (shown * PERCENT).roundToInt())
    Box(
        modifier
            .height(Spacing.TouchTarget)
            .testTag(DECK_SCRUBBER_TAG)
            .semantics {
                contentDescription = label
                stateDescription = value
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target ->
                    latestSeek(target.coerceIn(0f, 1f))
                    latestFinished()
                    true
                }
            }.pointerInput(Unit) {
                val inset = Spacing.M.toPx()

                fun fractionAt(x: Float) = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                detectTapGestures { at ->
                    latestSeek(fractionAt(at.x))
                    latestFinished()
                }
            }.pointerInput(Unit) {
                val inset = Spacing.M.toPx()

                fun fractionAt(x: Float) = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { at -> latestSeek(fractionAt(at.x)) },
                    onDragEnd = { latestFinished() },
                    onDragCancel = { latestFinished() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestSeek(fractionAt(change.position.x))
                    },
                )
            }.drawBehind {
                val inset = Spacing.M.toPx()
                val y = size.height / 2f
                val track = (size.width - 2 * inset).coerceAtLeast(1f)
                val x = inset + track * shown
                val stroke = Spacing.Hairline.toPx()
                drawLine(palette.rule, Offset(inset, y), Offset(inset + track, y), stroke)
                drawLine(palette.accent, Offset(inset, y), Offset(x, y), stroke)
                drawCircle(palette.accent, ThumbRadius.toPx(), Offset(x, y))
            },
    )
}

private const val PERCENT = 100f
