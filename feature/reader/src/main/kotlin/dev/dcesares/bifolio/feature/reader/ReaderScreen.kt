package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.designsystem.BifolioButton
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Heading
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Same zones as the page turns (NAV-02): outer 30% each side, so the middle 40% is the chrome tap. */
private const val CENTER_START = 0.30f
private const val CENTER_END = 0.70f
private const val BIG_HEADING_GAP = 1.5f
private const val HEADING_AFTER_GAP = 0.5f
private const val BIG_HEADING_MAX_LEVEL = 2

/** Chooses what to show for the view model state: nothing while loading, a failure card, or the page. */
@Composable
public fun ReaderRoute(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    when (val current = state) {
        is ReaderUiState.Ready ->
            Box(modifier.fillMaxSize()) {
                // A new surface per document and mode; the anchor carries the place across (ANC-04).
                key(current.contentHash, mode) {
                    // Read once per surface: the surface owns the anchor while it is shown.
                    val start = remember { viewModel.anchor.value }
                    if (mode == ReadingMode.PAGED) {
                        val paged by viewModel.paged.collectAsStateWithLifecycle()
                        PagedSurface(
                            layout = paged,
                            initialAnchor = start,
                            onRequest = viewModel::paginate,
                            onSettled = viewModel::onAnchorSettled,
                            onFirstDrawn = viewModel::onFirstSpreadDrawn,
                            onToggleChrome = viewModel::toggleChrome,
                            notices = current.notices,
                            foldSource = rememberFoldSource(),
                            document = current.document,
                        )
                    } else {
                        ReaderScreen(
                            ready = current,
                            onAnchorSettled = viewModel::onAnchorSettled,
                            startAnchor = start,
                            onToggleChrome = viewModel::toggleChrome,
                        )
                    }
                }
                // Outside the key: switching mode swaps the page surface, never the chrome.
                ReaderChrome(
                    visible = chromeVisible,
                    title = current.title,
                    mode = mode,
                    canSwitchMode = !current.scrollOnly,
                    onSwitchMode = viewModel::toggleMode,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        is ReaderUiState.Failed -> ReaderFailure(current.reason, onBack, modifier)
        ReaderUiState.Idle, ReaderUiState.Loading ->
            Box(modifier.fillMaxSize().background(BifolioTheme.palette.page))
    }
}

/**
 * Scroll mode reading surface (LAY-03, LAY-05, LAY-09). One LazyColumn item per top-level block, so the item
 * index is the block index and an anchor maps straight to a scroll position (ANC-01).
 */
@Composable
public fun ReaderScreen(
    ready: ReaderUiState.Ready,
    onAnchorSettled: (Anchor) -> Unit,
    modifier: Modifier = Modifier,
    startAnchor: Anchor = ready.initialAnchor,
    onToggleChrome: (() -> Unit)? = null,
) {
    val palette = BifolioTheme.palette
    val blocks = ready.document.blocks
    val gap = paragraphGap()
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val start = startAnchor.blockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val slugs = remember(blocks) { SlugIndex(blocks) }
    val onLink =
        remember(slugs, reducedMotion, listState) {
            { destination: String ->
                val target = slugs.blockIndexOf(destination)
                if (target != null) {
                    scope.launch {
                        if (reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
                    }
                }
                // TODO(NAV-08, M5): external links open only after confirmation. They are ignored until then.
            }
        }

    LaunchedEffect(ready.contentHash) {
        val anchor = startAnchor
        val blockLength = blocks.getOrNull(start)?.range?.length ?: 0
        if (anchor.blockIndex == start && anchor.charOffset > 0 && blockLength > 0) {
            val height =
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == start }
                        ?.size
                }.filterNotNull()
                    .first()
            val fraction = anchor.charOffset.toFloat() / blockLength
            listState.scrollToItem(start, (fraction * height).toInt())
        }
        // Only report once a scroll ends, so the restore above never overwrites the stored anchor.
        snapshotFlow { listState.isScrollInProgress }.drop(1).filter { !it }.collect {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@collect
            val length = blocks.getOrNull(first.index)?.range?.length ?: 0
            val fraction = listState.firstVisibleItemScrollOffset.toFloat() / first.size.coerceAtLeast(1)
            onAnchorSettled(Anchor(first.index, (fraction * length).toInt().coerceIn(0, length)))
        }
    }

    var noticesDismissed by rememberSaveable { mutableStateOf(false) }
    val showNotices = ready.notices.isNotEmpty() && !noticesDismissed
    val insets = ReaderInsets.asPaddingValues()
    val direction = LocalLayoutDirection.current

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(palette.page)
            // NAV-02: a center tap shows or hides the chrome. It never changes the reading mode.
            .pointerInput(onToggleChrome) {
                if (onToggleChrome != null) {
                    detectTapGestures { offset ->
                        val x = offset.x / size.width
                        if (x in CENTER_START..CENTER_END) onToggleChrome()
                    }
                }
            },
    ) {
        val insetStart = insets.calculateStartPadding(direction)
        val insetEnd = insets.calculateEndPadding(direction)
        val usable = maxWidth - insetStart - insetEnd
        val margin = maxOf(Spacing.PageMarginMin, usable * Spacing.PAGE_MARGIN_FRACTION)
        val side = maxOf(margin, (usable - Spacing.MaxMeasure) / 2)
        Column(Modifier.fillMaxSize()) {
            if (showNotices) {
                NoticeStack(
                    notices = ready.notices,
                    onDismiss = { noticesDismissed = true },
                    modifier =
                        Modifier.padding(
                            start = insetStart + side,
                            end = insetEnd + side,
                            top = insets.calculateTopPadding(),
                        ),
                )
            }
            CompositionLocalProvider(LocalLinkHandler provides onLink) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = insetStart + side,
                            end = insetEnd + side,
                            top = if (showNotices) Spacing.L else insets.calculateTopPadding() + Spacing.Xl,
                            bottom = insets.calculateBottomPadding() + Spacing.Xl,
                        ),
                ) {
                    itemsIndexed(blocks, key = { index, _ -> index }) { index, block ->
                        val top = if (block is Heading && index > 0) headingTop(block.level, gap) else 0.dp
                        val bottom = if (block is Heading) gap * HEADING_AFTER_GAP else gap
                        BlockContent(block, Modifier.padding(top = top, bottom = bottom))
                    }
                }
            }
        }
    }
}

private fun headingTop(
    level: Int,
    gap: Dp,
): Dp = if (level <= BIG_HEADING_MAX_LEVEL) gap * BIG_HEADING_GAP else gap

@Composable
internal fun NoticeStack(
    notices: Set<ReaderNotice>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = Spacing.S)
                .clip(RoundedCornerShape(Spacing.CodeRadius))
                .background(palette.codeSurface)
                .clickable(role = Role.Button, onClick = onDismiss)
                .defaultMinSize(minHeight = Spacing.TouchTarget)
                .padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        BifolioIcon(BifolioIcons.Info, contentDescription = null, tint = palette.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            notices.forEach { notice ->
                val message =
                    when (notice) {
                        ReaderNotice.ScrollOnlyLargeFile -> stringResource(R.string.reader_notice_scroll_only)
                        ReaderNotice.Windows1252Fallback -> stringResource(R.string.reader_notice_windows_1252)
                    }
                BasicText(message, style = type.caption.copy(color = palette.ink))
            }
            BasicText(
                stringResource(R.string.reader_notice_dismiss),
                style = type.caption.copy(color = palette.accent, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
internal fun ReaderFailure(
    reason: FailureReason,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val message =
        stringResource(
            when (reason) {
                FailureReason.NotMarkdown -> R.string.reader_failed_not_markdown
                FailureReason.TooLarge -> R.string.reader_failed_too_large
                FailureReason.Unreadable -> R.string.reader_failed_unreadable
            },
        )
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    Column(
        modifier
            .fillMaxSize()
            .background(palette.page)
            .padding(
                start = insets.calculateStartPadding(direction) + Spacing.PageMarginMin,
                end = insets.calculateEndPadding(direction) + Spacing.PageMarginMin,
                top = insets.calculateTopPadding() + Spacing.Xxxl,
                bottom = insets.calculateBottomPadding() + Spacing.Xl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Xl),
    ) {
        BasicText(message, style = BifolioTheme.typography.body.copy(textAlign = TextAlign.Center))
        BifolioButton(
            text = stringResource(R.string.reader_back_to_library),
            onClick = onBack,
            icon = BifolioIcons.ChevronLeft,
        )
    }
}
