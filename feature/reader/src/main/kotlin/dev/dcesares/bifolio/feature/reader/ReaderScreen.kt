package dev.dcesares.bifolio.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import dev.dcesares.bifolio.designsystem.selection
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.SearchHighlighter
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.Heading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
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
    appearanceSheet: @Composable (onDismiss: () -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val volumeKeysTurn by viewModel.volumeKeysTurn.collectAsStateWithLifecycle()
    val returnAnchor by viewModel.returnAnchor.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    var tocCurrent by rememberSaveable { mutableStateOf<Int?>(null) }
    var openNote by remember { mutableStateOf<NoteTap?>(null) }
    // NAV-08: the external link waiting for the reader's confirmation, and whether Open found no app.
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }
    var linkNoApp by remember { mutableStateOf(false) }
    val foldSource = rememberFoldSource()
    var footerHeight by remember { mutableIntStateOf(0) }
    var searchBarHeight by remember { mutableIntStateOf(0) }
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val timeLeft by viewModel.timeLeft.collectAsStateWithLifecycle()
    when (val current = state) {
        is ReaderUiState.Ready ->
            BoxWithConstraints(modifier.fillMaxSize()) {
                // UI-08: the sheet or pane rule is the TOC's, from the window width only (LAY-01).
                val compact = !isSidePane(maxWidth)
                val searchState by viewModel.search.collectAsStateWithLifecycle()
                val searchOpen = searchState.panel != SearchPanel.Closed
                val searchHighlighter =
                    remember(current.document, searchState.query, searchState.result, searchState.current, searchOpen) {
                        if (searchState.showsHighlights) {
                            SearchHighlighter(
                                current.document,
                                searchState.query,
                                searchState.result,
                                searchState.current,
                            )
                        } else {
                            null
                        }
                    }
                val fold by foldSource.fold.collectAsStateWithLifecycle(initialValue = foldSource.initialFold)
                val density = LocalDensity.current
                // The deck of tabletop posture has its own search button; the floating one would sit on it.
                val inTabletop = fold?.let { it.isSeparating && it.orientation == FoldOrientation.Horizontal } == true
                val closeOverlay: () -> Boolean = {
                    when {
                        pendingLink != null -> {
                            pendingLink = null
                            true
                        }
                        openNote != null -> {
                            openNote = null
                            true
                        }
                        searchOpen -> {
                            viewModel.backSearch(compact)
                            true
                        }
                        tocOpen -> {
                            tocOpen = false
                            true
                        }
                        else -> false
                    }
                }
                val contents = remember(current.document) { TableOfContents(current.document.blocks) }
                // NAV-07, UI-09: one resolver per document turns a tapped destination or marker into an action.
                val resolver = remember(current.document) { LinkResolver(current.document.blocks) }
                val onLink: (String) -> Unit = { destination ->
                    when (val action = resolver.resolve(destination)) {
                        is LinkAction.Jump -> {
                            openNote = null
                            viewModel.jumpTo(action.anchor)
                        }
                        is LinkAction.External -> {
                            linkNoApp = false
                            pendingLink = action.url
                        }
                        LinkAction.None -> Unit
                    }
                }
                val onNote: (NoteTap) -> Unit = { tap -> if (resolver.definitionOf(tap.label) != null) openNote = tap }
                // UI-07: no headings, no way in.
                val openToc: (() -> Unit)? =
                    if (contents.isEmpty) {
                        null
                    } else {
                        {
                            viewModel.keepChrome()
                            tocCurrent = contents.currentIndex(viewModel.anchor.value)
                            tocOpen = true
                        }
                    }
                val anyOverlay =
                    searchState.panel == SearchPanel.List || tocOpen || openNote != null || pendingLink != null
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
                            foldSource = foldSource,
                            document = current.document,
                            onOpenToc = openToc,
                            showToc = !contents.isEmpty,
                            volumeKeysTurn = volumeKeysTurn,
                            appearance = appearance,
                            jumps = viewModel.jumps,
                            onOpenSearch = viewModel::openSearch,
                            onCloseOverlay = closeOverlay,
                            onLink = onLink,
                            onNote = onNote,
                            overlayOpen = anyOverlay,
                            searchHighlighter = searchHighlighter,
                        )
                    } else {
                        ReaderScreen(
                            ready = current,
                            onAnchorSettled = viewModel::onAnchorSettled,
                            startAnchor = start,
                            onToggleChrome = viewModel::toggleChrome,
                            onOpenToc = openToc,
                            volumeKeysTurn = volumeKeysTurn,
                            jumps = viewModel.jumps,
                            onOpenSearch = viewModel::openSearch,
                            onCloseOverlay = closeOverlay,
                            onLink = onLink,
                            onNote = onNote,
                            overlayOpen = anyOverlay,
                            searchHighlighter = searchHighlighter,
                        )
                    }
                }
                if (appearance.hairline) {
                    ProgressHairline(progress, Modifier.align(Alignment.BottomCenter))
                }
                // Outside the key: switching mode swaps the page surface, never the chrome.
                ReaderChrome(
                    visible = chromeVisible,
                    appearance = appearance,
                    mode = mode,
                    actions =
                        ChromeActions(
                            onBack = onBack,
                            onOpenAppearance = {
                                viewModel.keepChrome()
                                sheetOpen = true
                            },
                            onMode = viewModel::setMode,
                            onAppearance = viewModel::updateAppearance,
                        ),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                ReaderFooter(
                    visible = chromeVisible,
                    timeLeft = timeLeft,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { footerHeight = it.height },
                )
                // After the footer, so it stays on top, and lifted by the footer's height while that is showing.
                ReturnChip(
                    visible = returnAnchor != null,
                    onReturn = viewModel::returnToJump,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom =
                                    with(LocalDensity.current) {
                                        if (searchState.panel == SearchPanel.Bar) {
                                            maxOf(footerHeight, searchBarHeight).toDp()
                                        } else {
                                            footerHeight.toDp()
                                        }
                                    },
                            ),
                )
                // UI-08: a way into search on a phone, with the chrome; Ctrl+F and the deck cover the rest.
                SearchButton(
                    visible = chromeVisible && !searchOpen && !tocOpen && !(mode == ReadingMode.PAGED && inTabletop),
                    onClick = viewModel::openSearch,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = Spacing.L,
                                bottom = with(LocalDensity.current) { footerHeight.toDp() } + Spacing.M,
                            ),
                )
                if (sheetOpen) {
                    BackHandler { sheetOpen = false }
                    appearanceSheet { sheetOpen = false }
                }
                if (!contents.isEmpty) {
                    if (tocOpen) BackHandler { tocOpen = false }
                    TocSheet(
                        state =
                            TocState(
                                contents = contents,
                                current = tocCurrent,
                                onSelect = { entry ->
                                    tocOpen = false
                                    viewModel.jumpTo(entry.anchor)
                                },
                                onDismiss = { tocOpen = false },
                            ),
                        visible = tocOpen,
                        clearance = with(density) { fold.tocClearance { it.toDp() } },
                    )
                }
                // After the TOC's handler, so Back closes search first when both were ever open.
                if (searchOpen) BackHandler { viewModel.backSearch(compact) }
                val searchActions =
                    remember(viewModel, compact) {
                        SearchActions(
                            onQuery = viewModel::setSearchQuery,
                            onSelect = { viewModel.selectSearchResult(it, collapseList = compact) },
                            onNext = viewModel::searchNext,
                            onPrevious = viewModel::searchPrevious,
                            onShowList = viewModel::showSearchList,
                            onBack = { viewModel.backSearch(compact) },
                            onClose = viewModel::closeSearch,
                        )
                    }
                SearchSheet(
                    state = searchState,
                    actions = searchActions,
                    visible = searchState.panel == SearchPanel.List,
                    clearance = with(density) { fold.tocClearance { it.toDp() } },
                )
                SearchBar(
                    state = searchState,
                    actions = searchActions,
                    visible = searchState.panel == SearchPanel.Bar,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { searchBarHeight = it.height },
                )
                // UI-09: last, so Back closes the note before search or contents, and it draws above them.
                if (openNote != null) BackHandler { openNote = null }
                val note = openNote
                val definition = note?.let { resolver.definitionOf(it.label) }
                FootnotePopover(
                    card =
                        if (note != null && definition != null) {
                            NoteCard(
                                tap = note,
                                definition = definition,
                                onGoTo = {
                                    openNote = null
                                    viewModel.jumpTo(Anchor(definition.blockIndex, 0))
                                },
                                onDismiss = { openNote = null },
                                onLink = onLink,
                                onNote = onNote,
                            )
                        } else {
                            null
                        },
                    fold = fold,
                )
                // NAV-08: above everything, so a link tapped inside a footnote card is confirmed on top of it.
                val context = LocalContext.current
                val link = pendingLink?.let(::parseExternalLink)
                if (link != null) BackHandler { pendingLink = null }
                ExternalLinkSheet(
                    prompt =
                        link?.let { checked ->
                            ExternalLinkPrompt(
                                link = checked,
                                noApp = linkNoApp,
                                onOpen = {
                                    if (context.openExternalLink(checked)) pendingLink = null else linkNoApp = true
                                },
                                onCancel = { pendingLink = null },
                            )
                        },
                    fold = fold,
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
internal fun ReaderScreen(
    ready: ReaderUiState.Ready,
    onAnchorSettled: (Anchor) -> Unit,
    modifier: Modifier = Modifier,
    startAnchor: Anchor = ready.initialAnchor,
    onToggleChrome: (() -> Unit)? = null,
    onOpenToc: (() -> Unit)? = null,
    volumeKeysTurn: Boolean = false,
    onLink: (String) -> Unit = {},
    onNote: (NoteTap) -> Unit = {},
    jumps: Flow<Anchor> = emptyFlow(),
    onOpenSearch: (() -> Unit)? = null,
    onCloseOverlay: (() -> Boolean)? = null,
    overlayOpen: Boolean = false,
    searchHighlighter: SearchHighlighter? = null,
) {
    val palette = BifolioTheme.palette
    val blocks = ready.document.blocks
    val gap = paragraphGap()
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val start = startAnchor.blockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)

    // ANC-06: the return chip asks for an anchor to be shown again.
    LaunchedEffect(jumps) { jumps.collect { listState.scrollToAnchor(blocks, it) } }

    LaunchedEffect(ready.contentHash) {
        listState.scrollToAnchor(blocks, startAnchor)
        // Only report once a scroll ends, so the restore above never overwrites the stored anchor. The position is
        // part of the key because a jump (Home, End, link) can start and end inside one snapshot (NAV-03).
        snapshotFlow {
            ScrollKey(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }.drop(1).filter { !it.scrolling }.collect {
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

    // NAV-05: the keyboard scrolls by 90% of the viewport; NAV-04 volume keys do the same when enabled.
    val focus = remember { FocusRequester() }
    LaunchedEffect(overlayOpen) { if (!overlayOpen) focus.requestFocus() }
    val keys = rememberUpdatedState(volumeKeysTurn)
    val openSearch = rememberUpdatedState(onOpenSearch)
    val closeOverlay = rememberUpdatedState(onCloseOverlay)
    // Markers report window px; the popover wants px from the top-left of this surface.
    val origin = remember { arrayOf(Offset.Zero) }
    val latestNote = rememberUpdatedState(onNote)
    val noteHere: (NoteTap) -> Unit =
        remember {
            { tap -> latestNote.value(NoteTap(tap.label, tap.bounds.translate(-origin[0]))) }
        }
    val selectionColors = TextSelectionColors(handleColor = palette.accent, backgroundColor = palette.selection)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(palette.page)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                val command = event.readerCommand(keys.value)
                // Search, close and the TOC need their openers; without them the key is not ours.
                val handled =
                    when (command) {
                        null -> false
                        ReaderCommand.SEARCH -> openSearch.value != null
                        ReaderCommand.CLOSE -> closeOverlay.value?.invoke() ?: false
                        ReaderCommand.TOC -> onOpenToc != null
                        else -> true
                    }
                if (handled) {
                    scope.launch {
                        val page = listState.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
                        val step = page * KEY_SCROLL_FRACTION
                        when (command) {
                            ReaderCommand.NEXT -> listState.scrollKeys(step, reducedMotion)
                            ReaderCommand.PREVIOUS -> listState.scrollKeys(-step, reducedMotion)
                            ReaderCommand.FIRST -> listState.scrollToItem(0)
                            ReaderCommand.LAST -> listState.scrollToItem((blocks.size - 1).coerceAtLeast(0))
                            ReaderCommand.TOC -> onOpenToc?.invoke()
                            ReaderCommand.SEARCH -> openSearch.value?.invoke()
                            else -> Unit
                        }
                    }
                }
                handled
            }.focusable()
            .onGloballyPositioned { origin[0] = it.positionInWindow() }
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
            CompositionLocalProvider(
                LocalLinkHandler provides onLink,
                LocalNoteHandler provides noteHere,
                LocalSearchHighlighter provides searchHighlighter,
            ) {
                // NAV-09: a long press selects text and the platform bar offers Copy, in reading palette colors.
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    SelectionContainer {
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

private suspend fun LazyListState.scrollKeys(
    distance: Float,
    reducedMotion: Boolean,
) {
    if (reducedMotion) scrollBy(distance) else animateScrollBy(distance)
}

private data class ScrollKey(
    val scrolling: Boolean,
    val index: Int,
    val offset: Int,
)

/** Scrolls so that [anchor] is at the top: its block first, then the fraction of the block its offset stands for. */
private suspend fun LazyListState.scrollToAnchor(
    blocks: List<Block>,
    anchor: Anchor,
) {
    val index = anchor.blockIndex.coerceIn(0, (blocks.size - 1).coerceAtLeast(0))
    val blockLength = blocks.getOrNull(index)?.range?.length ?: 0
    scrollToItem(index)
    if (anchor.blockIndex == index && anchor.charOffset > 0 && blockLength > 0) {
        val height =
            snapshotFlow { layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size }
                .filterNotNull()
                .first()
        val fraction = anchor.charOffset.toFloat() / blockLength
        scrollToItem(index, (fraction * height).toInt())
    }
}
