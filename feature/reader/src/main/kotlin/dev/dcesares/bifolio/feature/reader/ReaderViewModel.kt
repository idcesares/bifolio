package dev.dcesares.bifolio.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ContentHash
import dev.dcesares.bifolio.data.FolderGrant
import dev.dcesares.bifolio.data.FolderGrantRepository
import dev.dcesares.bifolio.data.FolderPaths
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.layout.CancellationSignal
import dev.dcesares.bifolio.layout.DocumentSearch
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.LayoutCache
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.PaginationService
import dev.dcesares.bifolio.layout.SearchResult
import dev.dcesares.bifolio.math.MathService
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.ReadingPace
import dev.dcesares.bifolio.model.WordIndex
import dev.dcesares.bifolio.model.plainText
import dev.dcesares.bifolio.parse.LoadResult
import dev.dcesares.bifolio.parse.MarkdownLoader
import dev.dcesares.bifolio.parse.SizeClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val MarkdownExtensions = listOf(".md", ".markdown", ".mdown", ".mkd")
private const val OCTET_STREAM = "application/octet-stream"
private const val SAVE_INTERVAL_MILLIS = 1_000L

/** The return chip leaves after this long without a tap (ANC-06). */
internal const val RETURN_CHIP_MILLIS = 8_000L

/** The chrome leaves after this long without interaction (UI-03). */
internal const val CHROME_AUTO_HIDE_MILLIS = 8_000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val PREVIEW_MAX = 140
private const val ELLIPSIS = "…"
private val WHITESPACE = Regex("""\s+""")

/** One line of the first top-level paragraph for the library (UI-15): plain text, collapsed, at most 140 characters. */
internal fun previewOf(document: Document): String {
    val paragraph = document.blocks.firstOrNull { it is Paragraph } as? Paragraph ?: return ""
    val text = plainText(paragraph.content).replace(WHITESPACE, " ").trim()
    if (text.length <= PREVIEW_MAX) return text
    var end = PREVIEW_MAX - ELLIPSIS.length
    if (Character.isHighSurrogate(text[end - 1])) end--
    return text.substring(0, end).trimEnd() + ELLIPSIS
}

/** Receives named durations in milliseconds, for the PRF-04 measurements. */
public fun interface PerfLog {
    public fun record(
        name: String,
        millis: Long,
    )

    public companion object {
        public val None: PerfLog = PerfLog { _, _ -> }
    }
}

/**
 * Opens documents and holds the loaded [Document] across configuration changes.
 * Parsing and hashing run on [computeDispatcher] (DOC-06); the main thread only publishes state.
 *
 * Paged mode (SCO-03): [paginate] runs the anchored pagination on [computeDispatcher] and publishes each
 * snapshot in [paged]. The reading [anchor] changes only when the reader settles on a spread or a scroll
 * position, never when the layout changes, so rotating back and forth cannot drift (ANC-03, ANC-04).
 */
@Suppress("LongParameterList", "TooManyFunctions") // Seams with defaults; one entry point per reader action.
public class ReaderViewModel(
    private val reader: DocumentReader,
    private val repository: RecentsRepository,
    private val preferences: ReadingPreferences,
    private val engine: TextEngine = AndroidTextEngine(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nanoClock: () -> Long = System::nanoTime,
    private val perf: PerfLog = PerfLog.None,
    private val chromeDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val searcher: (Document, String, () -> Unit) -> SearchResult = { document, query, cancelled ->
        DocumentSearch.search(document, query, checkCancelled = cancelled)
    },
    private val folders: FolderGrantRepository? = null,
    private val imageIo: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    public val state: StateFlow<ReaderUiState> = mutableState

    private val mutableAnchor = MutableStateFlow(Anchor(0, 0))

    /** Where the reader is. Changes on open and when a turn or a scroll settles (ANC-02). */
    public val anchor: StateFlow<Anchor> = mutableAnchor

    /** Document progress, 0 to 1, for the progress hairline (UI-05). Follows the settled anchor. */
    public val progress: StateFlow<Float> =
        combine(mutableAnchor, mutableState) { anchor, current ->
            (current as? ReaderUiState.Ready)?.let { progressOf(it.document, anchor) } ?: 0f
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    private val pace = ReadingPace()
    private var wordIndex: Pair<Document, WordIndex>? = null
    private var lastSettledAt: Long? = null

    /** Minutes left in the section and the document at the reader's own pace (UI-06). Null until a document is open. */
    public val timeLeft: StateFlow<TimeLeft?> =
        combine(mutableAnchor, mutableState) { anchor, current ->
            (current as? ReaderUiState.Ready)?.let { ready ->
                val left = wordIndexOf(ready.document).remaining(anchor)
                TimeLeft(
                    percent = (progressOf(ready.document, anchor) * PERCENT).toInt(),
                    documentMinutes = pace.minutesFor(left.inDocument),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun wordIndexOf(document: Document): WordIndex =
        wordIndex?.takeIf { it.first === document }?.second
            ?: WordIndex(document).also { wordIndex = document to it }

    private val mutablePaged = MutableStateFlow<PagedLayout?>(null)

    /** Latest pagination snapshot for the last [paginate] request, null until the first one arrives. */
    public val paged: StateFlow<PagedLayout?> = mutablePaged

    /** Columns the current layout actually uses, for the note under the columns choice. Null before pagination. */
    public val fittingColumns: StateFlow<Int?> =
        mutablePaged
            .map { it?.geometry?.columnCount }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The mode shown: the global preference, except that files too large for pages always scroll (DOC-05). */
    public val mode: StateFlow<ReadingMode> =
        combine(preferences.mode, mutableState) { preferred, current ->
            if ((current as? ReaderUiState.Ready)?.scrollOnly == true) ReadingMode.SCROLL else preferred
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ReadingMode.PAGED)

    /** Whether the volume keys turn pages (NAV-04). */
    public val volumeKeysTurn: StateFlow<Boolean> =
        preferences.volumeKeysTurn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The global appearance profile (UI-10). */
    public val appearance: StateFlow<AppearanceProfile> =
        preferences.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceProfile())

    public fun updateAppearance(change: (AppearanceProfile) -> AppearanceProfile) {
        keepChrome()
        viewModelScope.launch { preferences.updateAppearance(change) }
    }

    private val mutableChromeVisible = MutableStateFlow(false)

    /**
     * Whether the chrome (and with it the system bars) is shown (UI-03, UI-04). Not persisted: a reader always
     * opens immersive. It lives here so rotation and folding keep it. Only a center tap or the auto-hide changes it.
     */
    public val chromeVisible: StateFlow<Boolean> = mutableChromeVisible

    private var chromeHiding: Job? = null

    private val mutableReturnAnchor = MutableStateFlow<Anchor?>(null)

    /**
     * Where the reader was before the last jump (table of contents, link, search result, footnote), or null when
     * no return chip is due (ANC-06). It lives here, not in a surface, so folds and rotation keep it.
     */
    public val returnAnchor: StateFlow<Anchor?> = mutableReturnAnchor

    private val mutableJumps = MutableSharedFlow<Anchor>(extraBufferCapacity = 1)

    /** Asks the surface on screen to show an anchor. Sent by the return chip and by the table of contents. */
    public val jumps: SharedFlow<Anchor> = mutableJumps

    private var returnExpiry: Job? = null

    private val pagination = PaginationService(LayoutCache())
    private var paginating: Job? = null
    private var lastRequest: PageRequest? = null
    private var lastImageKey = 0
    private var lastMathKey = 0
    private var requestedAt = 0L
    private var firstDrawReported = false

    private var loading: Job? = null
    private var currentUri: String? = null
    private var lastSavedAt: Long? = null
    private var pending: Anchor? = null

    /** Center tap (NAV-02): shows the chrome, or hides it. Never touches the reading mode. */
    public fun toggleChrome() {
        if (mutableChromeVisible.value) hideChrome() else showChrome()
    }

    /** Any use of the chrome restarts the 4 s countdown (UI-03). */
    public fun keepChrome() {
        if (mutableChromeVisible.value) showChrome()
    }

    /** Shows the chrome and restarts the countdown: Tab on the reading surface asks for it (A11Y-06). */
    public fun showChrome() {
        mutableChromeVisible.value = true
        chromeHiding?.cancel()
        chromeHiding =
            viewModelScope.launch(chromeDispatcher) {
                delay(CHROME_AUTO_HIDE_MILLIS)
                mutableChromeVisible.value = false
            }
    }

    private fun hideChrome() {
        chromeHiding?.cancel()
        mutableChromeVisible.value = false
    }

    /** A jump is about to move the reader: remember where it is now and offer the way back (ANC-06). */
    public fun onJump() {
        mutableReturnAnchor.value = mutableAnchor.value
        startReturnExpiry()
    }

    private fun startReturnExpiry() {
        returnExpiry?.cancel()
        returnExpiry =
            viewModelScope.launch(chromeDispatcher) {
                delay(RETURN_CHIP_MILLIS)
                mutableReturnAnchor.value = null
            }
    }

    /**
     * Jumps to [target] from the table of contents (UI-07): remembers where the reader is for the return chip, then
     * asks the surface on screen to show the anchor (ANC-06). The target is an anchor, never a page (ANC-01).
     */
    public fun jumpTo(target: Anchor) {
        searchOwnsReturn = false
        onJump()
        mutableJumps.tryEmit(target)
    }

    private val mutableSearch = MutableStateFlow(SearchState())

    /** The search session: query, results, current result and what of it is on screen (UI-08). */
    internal val search: StateFlow<SearchState> = mutableSearch

    private var searching: Job? = null

    /** True while the return chip on screen was started by a search jump, so the next result keeps its origin. */
    private var searchOwnsReturn = false

    /** Opens the result list (Ctrl+F, the search button). A document must be open. */
    internal fun openSearch() {
        if (mutableState.value !is ReaderUiState.Ready) return
        keepChrome()
        mutableSearch.value = mutableSearch.value.opened()
    }

    /**
     * The query changed. A blank one clears at once. Otherwise the engine runs on [computeDispatcher] after
     * [SEARCH_DEBOUNCE_MILLIS] without another keystroke; a newer query cancels the running one, which the engine
     * notices at its next text unit.
     */
    internal fun setSearchQuery(text: String) {
        searching?.cancel()
        mutableSearch.value = mutableSearch.value.withQuery(text)
        val ready = mutableState.value as? ReaderUiState.Ready ?: return
        if (text.isBlank()) return
        searching =
            viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MILLIS)
                val found = withContext(computeDispatcher) { searcher(ready.document, text) { ensureActive() } }
                mutableSearch.value = mutableSearch.value.withResult(found)
            }
    }

    internal fun searchNext() = stepSearch { it.next() }

    internal fun searchPrevious() = stepSearch { it.previous() }

    private fun stepSearch(step: (SearchState) -> SearchState) {
        keepChrome()
        val next = step(mutableSearch.value)
        mutableSearch.value = next
        jumpToCurrentMatch(next)
    }

    /** A result was chosen in the list. [collapseList] is true on a compact window, where the bar takes over. */
    internal fun selectSearchResult(
        index: Int,
        collapseList: Boolean,
    ) {
        keepChrome()
        val next = mutableSearch.value.selected(index, collapseList)
        mutableSearch.value = next
        jumpToCurrentMatch(next)
    }

    /**
     * Shows the current match. Only the first jump of a session records the position the reader came from, so
     * the return chip leads back to where the search began, not to the previous result (ANC-06).
     */
    private fun jumpToCurrentMatch(state: SearchState) {
        val target = state.currentMatch?.anchor ?: return
        if (searchOwnsReturn && mutableReturnAnchor.value != null) {
            startReturnExpiry()
        } else {
            onJump()
            searchOwnsReturn = true
        }
        mutableJumps.tryEmit(target)
    }

    internal fun showSearchList() {
        mutableSearch.value = mutableSearch.value.listShown()
    }

    /** Back or Escape in search: one level at a time. [hasBar] is true on a compact window. */
    internal fun backSearch(hasBar: Boolean) {
        val next = mutableSearch.value.backed(hasBar)
        if (next.panel == SearchPanel.Closed) closeSearch() else mutableSearch.value = next
    }

    /** Ends the session: results and highlights go; a running search stops. */
    internal fun closeSearch() {
        searching?.cancel()
        searchOwnsReturn = false
        mutableSearch.value = SearchState()
    }

    /** The chip was tapped: go back to the pre-jump anchor. The chip is gone afterwards. */
    public fun returnToJump() {
        val target = mutableReturnAnchor.value ?: return
        dismissReturn()
        mutableJumps.tryEmit(target)
    }

    public fun dismissReturn() {
        returnExpiry?.cancel()
        mutableReturnAnchor.value = null
    }

    public fun open(request: OpenRequest) {
        hideChrome()
        closeSearch()
        dismissReturn()
        flush()
        loading?.cancel()
        mutableState.value = ReaderUiState.Loading
        currentUri = null
        lastSavedAt = null
        pending = null
        resetPaging()
        stopImages()
        stopMath()
        loading =
            viewModelScope.launch {
                val loaded = load(request)
                mutableState.value = loaded
                if (loaded is ReaderUiState.Ready) startImages(loaded, request.uri)
            }
    }

    /** Back to the library: forgets the loaded document. */
    public fun close() {
        hideChrome()
        closeSearch()
        dismissReturn()
        flush()
        loading?.cancel()
        currentUri = null
        resetPaging()
        stopImages()
        stopMath()
        mutableState.value = ReaderUiState.Idle
    }

    private fun resetPaging() {
        paginating?.cancel()
        lastRequest = null
        lastImageKey = 0
        lastMathKey = 0
        mutablePaged.value = null
    }

    private suspend fun load(request: OpenRequest): ReaderUiState {
        val raw =
            try {
                reader.read(request.uri)
            } catch (_: IOException) {
                return ReaderUiState.Failed(FailureReason.Unreadable)
            }
        val bytes = raw.bytes
        val refusal = refusalFor(request, raw)
        if (bytes == null || refusal != null) return ReaderUiState.Failed(refusal ?: FailureReason.TooLarge)
        val (result, hash) =
            withContext(computeDispatcher) { MarkdownLoader.load(bytes, raw.declaredSize) to ContentHash.of(bytes) }
        val loaded = result as? LoadResult.Loaded ?: return ReaderUiState.Failed(FailureReason.TooLarge)

        if (request.persistGrant) repository.persistGrant(request.uri)
        val document = loaded.document
        val name = raw.displayName ?: request.uri.substringAfterLast('/')
        val anchor = restoreAnchor(document, repository.anchorFor(request.uri))
        val title = document.title?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.', name)
        repository.upsert(
            RecentDocument(request.uri, title, name, clock(), progressOf(document, anchor), hash, previewOf(document)),
        )
        currentUri = request.uri
        val scrollOnly = loaded.sizeClass == SizeClass.SCROLL_ONLY
        val notices =
            buildSet {
                if (scrollOnly) add(ReaderNotice.ScrollOnlyLargeFile)
                if (loaded.encodingWarning) add(ReaderNotice.Windows1252Fallback)
            }
        mutableAnchor.value = anchor
        return ReaderUiState.Ready(document, title, hash, notices, anchor, scrollOnly)
    }

    /**
     * Switches between pages and scroll and stores the choice as the global profile (SCO-03). The anchor
     * carries over, so the same passage stays in view (ANC-04). Called only from the chrome's mode control.
     * TEMPORARY until the appearance sheet owns the reading mode (UI-10, M4); no tap on the page may call it.
     */
    public fun toggleMode() {
        keepChrome()
        if ((mutableState.value as? ReaderUiState.Ready)?.scrollOnly != false) return
        val next = if (mode.value == ReadingMode.PAGED) ReadingMode.SCROLL else ReadingMode.PAGED
        viewModelScope.launch { preferences.setMode(next) }
    }

    /** Sets the reading mode from the appearance sheet (SCO-03). Files too large for pages stay in scroll. */
    public fun setMode(next: ReadingMode) {
        keepChrome()
        viewModelScope.launch { preferences.setMode(next) }
    }

    public fun setVolumeKeysTurn(enabled: Boolean) {
        viewModelScope.launch { preferences.setVolumeKeysTurn(enabled) }
    }

    /**
     * Starts (or restarts) the anchored pagination for [request] (PAG-09, PAG-10). An identical request is
     * ignored; a different one cancels the running pagination, so a window that keeps resizing never queues work.
     */
    public fun paginate(request: PageRequest) {
        val ready = mutableState.value as? ReaderUiState.Ready ?: return
        // One snapshot per run: the paginator and the detailer see the sizes the key stands for (PAG-07).
        val snapshot = imageSession.value?.sizes?.value ?: ImageSizeSnapshot.EMPTY
        val (math, mathKey) = (mathSession.value?.sizes?.value ?: MathSizeSnapshot.EMPTY).forRequest(request)
        val unchanged = request == lastRequest && snapshot.key == lastImageKey && mathKey == lastMathKey
        if (ready.scrollOnly || unchanged) return
        lastRequest = request
        lastImageKey = snapshot.key
        lastMathKey = mathKey
        val images = snapshot.scaledBy(request.density)
        paginating?.cancel()
        // The previous layout stays up until the new one has its first stage, so a change never blanks the page.
        firstDrawReported = false
        val started = nanoClock()
        requestedAt = started
        val from = mutableAnchor.value
        paginating =
            viewModelScope.launch(computeDispatcher) {
                val text = engine.prepare(request)
                val geometry = GeometryCalculator.compute(text.spec)
                val detailer = PageDetailer(ready.document, text.measurer, geometry, images, math)
                val cancel = CancellationSignal { !isActive }
                pagination.paginate(
                    ready.contentHash,
                    ready.document,
                    text.measurer,
                    geometry,
                    from,
                    cancel,
                    images,
                    snapshot.key,
                    math,
                    mathKey,
                ) { flow ->
                    if (!isActive) return@paginate
                    val layout = PagedLayout(request, geometry, text.config, flow, detailer, snapshot.key, mathKey)
                    perf.record(
                        "pagination.${layout.stage.name.lowercase()}",
                        (nanoClock() - started) / NANOS_PER_MILLI,
                    )
                    mutablePaged.value = layout
                }
            }
    }

    private val mutableMath = MutableStateFlow<MathSession?>(null)

    /** The display formulas of the open document: sizes, pixels and failures (SYN-12, PAG-07). */
    internal val mathSession: StateFlow<MathSession?> = mutableMath

    private var mathScope: CoroutineScope? = null
    private var mathWatcher: Job? = null
    private var mathHash: String? = null

    private fun stopMath() {
        mathWatcher?.cancel()
        mathScope?.cancel()
        mathWatcher = null
        mathScope = null
        mathHash = null
        mutableMath.value = null
    }

    /**
     * Gives the open document the math engine of the window's density and text scale, and the body size the
     * formulas are measured at. Measuring happens off the main thread; each batch of sizes is a new snapshot and so
     * a new pagination from the reading anchor (ANC-03). Called again when the engine or the size changes.
     */
    internal fun bindMath(
        engine: MathService,
        fontSp: Float,
    ) {
        val ready = mutableState.value as? ReaderUiState.Ready ?: return
        var session = mutableMath.value
        if (session == null || mathHash != ready.contentHash) {
            stopMath()
            val scope = CoroutineScope(SupervisorJob() + computeDispatcher)
            mathScope = scope
            mathHash = ready.contentHash
            session = MathSession(ready.document, scope)
            mutableMath.value = session
            val watched = session
            mathWatcher =
                viewModelScope.launch {
                    watched.sizes.collect { snapshot ->
                        val request = lastRequest
                        if (request != null && snapshot.key != lastMathKey) paginate(request)
                    }
                }
        }
        session.bind(engine, fontSp)
    }

    private val mutableImages = MutableStateFlow<ImageSession?>(null)

    /** The images of the open document: sizes, pixels and where they come from (SYN-08, PAG-07). */
    internal val imageSession: StateFlow<ImageSession?> = mutableImages

    private val mutableImageBanner = MutableStateFlow<ImageBanner>(ImageBanner.Hidden)

    /** Whether to offer the folder of the open document, for its relative images (UI-14). */
    public val imageBanner: StateFlow<ImageBanner> = mutableImageBanner

    private var imageScope: CoroutineScope? = null
    private var sizeWatcher: Job? = null

    private fun stopImages() {
        sizeWatcher?.cancel()
        imageScope?.cancel()
        sizeWatcher = null
        imageScope = null
        mutableImages.value = null
        mutableImageBanner.value = ImageBanner.Hidden
    }

    private suspend fun startImages(
        ready: ReaderUiState.Ready,
        uri: String,
    ) {
        val grant = folders?.find(uri)
        val session = beginImages(ready, grant)
        val asks = folders != null && grant == null && session.needsFolder()
        if (asks && !folders.isDismissed(uri)) {
            mutableImageBanner.value = ImageBanner.Offer(FolderPaths.pickerStart(uri))
        }
    }

    private fun beginImages(
        ready: ReaderUiState.Ready,
        grant: FolderGrant?,
    ): ImageSession {
        sizeWatcher?.cancel()
        imageScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + computeDispatcher)
        imageScope = scope
        val repository = folders
        val session =
            ImageSession(
                ready.document,
                grant,
                { segments -> if (grant != null && repository != null) repository.open(grant, segments) else null },
                scope,
                imageIo,
            )
        mutableImages.value = session
        session.start()
        // Sizes arrive in batches; each batch is a new snapshot and so a new pagination from the reading anchor.
        sizeWatcher =
            viewModelScope.launch {
                session.sizes.collect { snapshot ->
                    val request = lastRequest
                    if (request != null && snapshot.key != lastImageKey) paginate(request)
                }
            }
        return session
    }

    /** The folder chooser returned [tree] (null when cancelled). It must hold the open document (UI-14). */
    public fun onFolderPicked(tree: String?) {
        val ready = mutableState.value as? ReaderUiState.Ready ?: return
        val uri = currentUri ?: return
        val repository = folders ?: return
        if (tree == null) return
        viewModelScope.launch {
            val grant = repository.add(tree, uri)
            // The document may have been closed or replaced while the provider was asked: that answer is not for it.
            val now = mutableState.value as? ReaderUiState.Ready
            if (currentUri != uri || now == null || now.contentHash != ready.contentHash) return@launch
            if (grant == null) {
                mutableImageBanner.value = ImageBanner.WrongFolder(FolderPaths.pickerStart(uri))
            } else {
                mutableImageBanner.value = ImageBanner.Hidden
                beginImages(ready, grant)
            }
        }
    }

    /** The banner was dismissed: the document is not asked about again (UI-14). */
    public fun dismissImageBanner() {
        mutableImageBanner.value = ImageBanner.Hidden
        val uri = currentUri ?: return
        val repository = folders ?: return
        viewModelScope.launch { repository.dismiss(uri) }
    }

    /** The surface drew its first spread for the current request: the "first spread" time of PRF-04. */
    public fun onFirstSpreadDrawn() {
        if (firstDrawReported) return
        firstDrawReported = true
        perf.record("firstSpreadDrawn", (nanoClock() - requestedAt) / NANOS_PER_MILLI)
    }

    /** Called when a turn or a scroll settles. Persists at most once per second; [flush] writes the rest (ANC-02). */
    public fun onAnchorSettled(anchor: Anchor) {
        recordPace(mutableAnchor.value, anchor)
        mutableAnchor.value = anchor
        pending = anchor
        val last = lastSavedAt
        if (last == null || clock() - last >= SAVE_INTERVAL_MILLIS) flush()
    }

    /** Feeds the reading speed with the words a forward move covered and the time it took (UI-06). */
    private fun recordPace(
        from: Anchor,
        to: Anchor,
    ) {
        val now = clock()
        val since = lastSettledAt
        lastSettledAt = now
        val ready = mutableState.value as? ReaderUiState.Ready ?: return
        if (since == null) return
        val index = wordIndexOf(ready.document)
        val words = index.wordsBetween(ready.document.sourceOffsetOf(from), ready.document.sourceOffsetOf(to))
        pace.recordTurn(words, now - since)
    }

    /** Writes the latest unsaved anchor now. Call from `onStop`. */
    public fun flush() {
        val anchor = pending ?: return
        val ready = mutableState.value as? ReaderUiState.Ready ?: return
        val uri = currentUri ?: return
        pending = null
        val at = clock()
        lastSavedAt = at
        val progress = progressOf(ready.document, anchor)
        viewModelScope.launch {
            repository.saveAnchor(uri, anchor)
            repository.updateProgress(uri, progress, at)
        }
    }

    private fun refusalFor(
        request: OpenRequest,
        raw: RawDocument,
    ): FailureReason? =
        when {
            request.mimeType == OCTET_STREAM && !isMarkdownName(raw.displayName) -> FailureReason.NotMarkdown
            raw.bytes == null -> FailureReason.TooLarge
            else -> null
        }

    private fun isMarkdownName(name: String?): Boolean =
        name != null && MarkdownExtensions.any { name.endsWith(it, ignoreCase = true) }

    private fun restoreAnchor(
        document: Document,
        stored: Anchor?,
    ): Anchor {
        if (stored == null || document.blocks.isEmpty()) return Anchor(0, 0)
        if (stored.blockIndex > document.blocks.lastIndex) return Anchor(document.blocks.lastIndex, 0)
        val length = document.blocks[stored.blockIndex].range.length
        return Anchor(stored.blockIndex, stored.charOffset.coerceAtMost(length))
    }

    /** Progress from the anchor's place in the source text: independent of geometry (ADR-0005). */
    private fun progressOf(
        document: Document,
        anchor: Anchor,
    ): Float =
        if (document.source.isEmpty()) {
            0f
        } else {
            (document.sourceOffsetOf(anchor).toFloat() / document.source.length).coerceIn(0f, 1f)
        }
}

private const val PERCENT = 100
