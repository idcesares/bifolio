@file:Suppress("TooManyFunctions") // Sheet, bar and button share one panel's pieces; splitting would scatter them.

package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import dev.dcesares.bifolio.designsystem.searchHit
import dev.dcesares.bifolio.layout.DocumentSearch
import dev.dcesares.bifolio.layout.SearchMatch

internal const val SEARCH_SHEET_TAG = "search-sheet"
internal const val SEARCH_FIELD_TAG = "search-field"
internal const val SEARCH_ROW_TAG = "search-row"
internal const val SEARCH_BAR_TAG = "search-bar"
internal const val SEARCH_BUTTON_TAG = "search-button"
internal const val SEARCH_PREVIOUS_TAG = "search-previous"
internal const val SEARCH_NEXT_TAG = "search-next"

private const val ROWS_ABOVE_CURRENT = 2
private const val DISABLED_ALPHA = 0.4f
private const val SNIPPET_LINES = 3

/** True when a window this wide shows search as a side pane rather than a sheet (LAY-01: from the window only). */
internal fun isSidePane(windowWidth: Dp): Boolean = windowWidth >= Spacing.SidePaneMinWindow

/** What the search surfaces can ask for. The caller turns each into a view model call (UI-08, ANC-06). */
internal class SearchActions(
    val onQuery: (String) -> Unit,
    val onSelect: (Int) -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onShowList: () -> Unit,
    /** Back, Escape and the scrim: one level at a time. */
    val onBack: () -> Unit,
    /** The close button: ends search. */
    val onClose: () -> Unit,
)

/**
 * Search results (UI-08): a bottom sheet on windows narrower than 840dp, a side pane at the end edge from 840dp,
 * decided from the window width only (LAY-01), the same rule and the same fold clearance as the table of contents.
 * The query field, the result count with previous and next, and the list of snippets sit in one panel.
 *
 * Motion (MOT-06, MOT-07): scrim and panel use the chrome springs, the panel sliding from its edge; under reduced
 * motion both fade. Toggling [visible] retargets the transition, so it can be interrupted at any frame.
 * [autoFocus] puts the cursor in the field when the panel opens; screenshot tests turn it off.
 */
@Composable
internal fun SearchSheet(
    state: SearchState,
    actions: SearchActions,
    visible: Boolean,
    modifier: Modifier = Modifier,
    clearance: TocClearance = TocClearance(),
    autoFocus: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val side = isSidePane(maxWidth)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(BifolioMotion.fade()),
            exit = fadeOut(BifolioMotion.fade()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = Spacing.SCRIM_ALPHA))
                    .pointerInput(Unit) { detectTapGestures { actions.onBack() } },
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
            SearchPanel(
                state,
                actions,
                side,
                maxWidth - clearance.start,
                maxHeight - clearance.top,
                clearance.top > 0.dp,
                autoFocus,
            )
        }
    }
}

@Composable
private fun SearchPanel(
    state: SearchState,
    actions: SearchActions,
    side: Boolean,
    availableWidth: Dp,
    availableHeight: Dp,
    belowFold: Boolean,
    autoFocus: Boolean,
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
            Modifier
                .widthIn(max = Spacing.SheetMaxWidth)
                .fillMaxWidth()
                .heightIn(max = if (belowFold) availableHeight else availableHeight * SHEET_MAX_HEIGHT)
        }
    Column(
        sizing
            // The edge in the rule color keeps the panel legible on Night (DESIGN section 5), like the notes sheet.
            .border(Spacing.Rule, palette.rule, shape)
            .clip(shape)
            .background(palette.page)
            .testTag(SEARCH_SHEET_TAG)
            // NAV-03: Escape closes, also while the field has the focus.
            .onPreviewKeyEvent { event ->
                if (event.readerCommand(false) == ReaderCommand.CLOSE) {
                    actions.onBack()
                    true
                } else {
                    false
                }
            }
            // Taps inside the panel must not reach the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = insets.calculateBottomPadding()),
    ) {
        SearchHeader(actions.onClose, Modifier.padding(top = if (side) insets.calculateTopPadding() else 0.dp))
        SearchField(state.query, actions, autoFocus)
        SearchStatus(state, actions)
        SearchResults(state, actions, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun SearchHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.L, end = Spacing.S, top = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = stringResource(R.string.reader_search_title),
            modifier = Modifier.weight(1f),
            style = BifolioTheme.typography.headings[1].copy(color = BifolioTheme.palette.ink),
        )
        BifolioIconButton(
            icon = BifolioIcons.ChevronDown,
            description = stringResource(R.string.reader_search_close),
            onClick = onClose,
        )
    }
}

@Composable
private fun SearchField(
    initial: String,
    actions: SearchActions,
    autoFocus: Boolean,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val hint = stringResource(R.string.reader_search_hint)
    // The text is held here, not read back from the view model: a field fed through an asynchronous flow drops
    // characters while the keyboard is composing.
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.L, vertical = Spacing.S)
            .defaultMinSize(minHeight = Spacing.TouchTarget)
            .clip(RoundedCornerShape(Spacing.SheetRadius))
            .background(palette.codeSurface)
            .padding(horizontal = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BifolioIcon(BifolioIcons.Search, contentDescription = null, tint = palette.inkMuted)
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                actions.onQuery(it)
            },
            modifier =
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = Spacing.TouchTarget)
                    .padding(start = Spacing.S)
                    .focusRequester(focus)
                    .testTag(SEARCH_FIELD_TAG)
                    .semantics { contentDescription = hint },
            singleLine = true,
            textStyle = type.body.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) BasicText(hint, style = type.body.copy(color = palette.inkMuted))
                    field()
                }
            },
        )
    }
}

/** The count line with previous and next. Empty before the first query, so the field has the sheet's attention. */
@Composable
private fun SearchStatus(
    state: SearchState,
    actions: SearchActions,
) {
    val palette = BifolioTheme.palette
    val hasResults = state.count > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.L,
                end = Spacing.S,
            ).defaultMinSize(minHeight = Spacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = countText(state) ?: "",
            modifier = Modifier.weight(1f),
            style = BifolioTheme.typography.caption.copy(color = palette.inkMuted),
        )
        SearchStepButton(
            BifolioIcons.ChevronLeft,
            stringResource(R.string.reader_search_previous),
            hasResults,
            actions.onPrevious,
            SEARCH_PREVIOUS_TAG,
        )
        SearchStepButton(
            BifolioIcons.ChevronRight,
            stringResource(R.string.reader_search_next),
            hasResults,
            actions.onNext,
            SEARCH_NEXT_TAG,
        )
    }
}

/** "3 of 12" once a result is current, "12 results" before, "Searching…" or the no-result line when empty. */
@Composable
private fun countText(state: SearchState): String? =
    when {
        state.query.isBlank() -> null
        state.count == 0 && state.phase == SearchPhase.Searching -> stringResource(R.string.reader_search_searching)
        state.count == 0 -> stringResource(R.string.reader_search_none, state.query.trim())
        state.current != NO_RESULT -> stringResource(R.string.reader_search_position, state.current + 1, state.count)
        else -> pluralStringResource(R.plurals.reader_search_results, state.count, state.count)
    }

@Composable
private fun SearchStepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        Modifier
            .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .testTag(tag)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BifolioIcon(icon, contentDescription = null, tint = BifolioTheme.palette.inkMuted)
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    if (state.query.isBlank()) {
        BasicText(
            stringResource(R.string.reader_search_idle),
            modifier = modifier.padding(horizontal = Spacing.L, vertical = Spacing.L),
            style = type.caption.copy(color = palette.inkMuted),
        )
        return
    }
    Column(modifier) {
        if (state.result.truncated) {
            BasicText(
                stringResource(R.string.reader_search_truncated, DocumentSearch.DEFAULT_LIMIT),
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
                style = type.caption.copy(color = palette.inkMuted),
            )
        }
        val listState = rememberLazyListState((state.current - ROWS_ABOVE_CURRENT).coerceAtLeast(0))
        LaunchedEffect(state.current) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (state.current != NO_RESULT && visible.none { it.index == state.current }) {
                listState.scrollToItem((state.current - 1).coerceAtLeast(0))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = Spacing.S),
        ) {
            itemsIndexed(state.matches, key = { index, _ -> index }) { index, match ->
                SearchRow(match, index == state.current) { actions.onSelect(index) }
            }
        }
    }
}

@Composable
private fun SearchRow(
    match: SearchMatch,
    isCurrent: Boolean,
    onSelect: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val currentLabel = stringResource(R.string.reader_search_current)
    val bar = palette.accent
    val hit = palette.searchHit
    val snippet = match.snippet
    val text =
        remember(snippet, hit) {
            buildAnnotatedString {
                append(snippet.text)
                val start = snippet.matchStart.coerceIn(0, snippet.text.length)
                val end = snippet.matchEnd.coerceIn(start, snippet.text.length)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = hit), start, end)
            }
        }
    Box(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.TouchTarget)
            .testTag(SEARCH_ROW_TAG)
            .background(if (isCurrent) palette.codeSurface else Color.Transparent)
            .drawBehind {
                if (isCurrent) drawRect(bar, Offset.Zero, Size(CurrentBar.toPx(), size.height))
            }.semantics(mergeDescendants = true) {
                selected = isCurrent
                if (isCurrent) stateDescription = currentLabel
            }.clickable(role = Role.Button, onClick = onSelect),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M),
            maxLines = SNIPPET_LINES,
            overflow = TextOverflow.Ellipsis,
            style = type.body.copy(color = palette.ink),
        )
    }
}

/**
 * The compact window's way of searching once a result is chosen: the sheet gives way to this bar, so the page and its
 * highlights are in view while the reader steps through results. It reports its height through [onHeight] so the return
 * chip can sit above it.
 */
@Composable
internal fun SearchBar(
    state: SearchState,
    actions: SearchActions,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val palette = BifolioTheme.palette
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val hasResults = state.count > 0
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = panelEnter(false, reduced),
        exit = panelExit(false, reduced),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.page)
                .testTag(SEARCH_BAR_TAG)
                .onPreviewKeyEvent { event ->
                    if (event.readerCommand(false) == ReaderCommand.CLOSE) {
                        actions.onBack()
                        true
                    } else {
                        false
                    }
                }
                // The bar sits over the page: its taps must not turn pages or hide the chrome.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(Modifier.fillMaxWidth().height(Spacing.Rule).background(palette.rule))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.L, end = Spacing.S, bottom = insets.calculateBottomPadding()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = countText(state) ?: "",
                    modifier = Modifier.weight(1f),
                    style = BifolioTheme.typography.body.copy(color = palette.ink),
                )
                SearchStepButton(
                    BifolioIcons.ChevronLeft,
                    stringResource(R.string.reader_search_previous),
                    hasResults,
                    actions.onPrevious,
                    SEARCH_PREVIOUS_TAG,
                )
                SearchStepButton(
                    BifolioIcons.ChevronRight,
                    stringResource(R.string.reader_search_next),
                    hasResults,
                    actions.onNext,
                    SEARCH_NEXT_TAG,
                )
                BifolioIconButton(
                    icon = BifolioIcons.List,
                    description = stringResource(R.string.reader_search_show_list),
                    onClick = actions.onShowList,
                )
                BifolioIconButton(
                    icon = BifolioIcons.ChevronDown,
                    description = stringResource(R.string.reader_search_close),
                    onClick = actions.onClose,
                )
            }
        }
    }
}

/** The way into search on a phone (UI-08): shown with the chrome, over the page at the end edge. */
@Composable
internal fun SearchButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(BifolioMotion.fade()),
        exit = fadeOut(BifolioMotion.fade()),
    ) {
        Box(
            Modifier
                .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
                .clip(CircleShape)
                .background(palette.page)
                .border(Spacing.Rule, palette.rule, CircleShape)
                .testTag(SEARCH_BUTTON_TAG)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BifolioIcon(
                BifolioIcons.Search,
                contentDescription = stringResource(R.string.reader_search_open),
                tint = palette.inkMuted,
            )
        }
    }
}
