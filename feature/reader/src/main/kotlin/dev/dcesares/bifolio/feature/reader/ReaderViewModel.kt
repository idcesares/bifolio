package dev.dcesares.bifolio.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ContentHash
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.layout.CancellationSignal
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.LayoutCache
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.PaginationService
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import dev.dcesares.bifolio.parse.LoadResult
import dev.dcesares.bifolio.parse.MarkdownLoader
import dev.dcesares.bifolio.parse.SizeClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
internal const val CHROME_AUTO_HIDE_MILLIS = 4_000L
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

    private val mutablePaged = MutableStateFlow<PagedLayout?>(null)

    /** Latest pagination snapshot for the last [paginate] request, null until the first one arrives. */
    public val paged: StateFlow<PagedLayout?> = mutablePaged

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

    /** Asks the surface on screen to show an anchor. Today only the return chip sends it. */
    public val jumps: SharedFlow<Anchor> = mutableJumps

    private var returnExpiry: Job? = null

    private val pagination = PaginationService(LayoutCache())
    private var paginating: Job? = null
    private var lastRequest: PageRequest? = null
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

    private fun showChrome() {
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
        returnExpiry?.cancel()
        returnExpiry =
            viewModelScope.launch(chromeDispatcher) {
                delay(RETURN_CHIP_MILLIS)
                mutableReturnAnchor.value = null
            }
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
        dismissReturn()
        flush()
        loading?.cancel()
        mutableState.value = ReaderUiState.Loading
        currentUri = null
        lastSavedAt = null
        pending = null
        resetPaging()
        loading = viewModelScope.launch { mutableState.value = load(request) }
    }

    /** Back to the library: forgets the loaded document. */
    public fun close() {
        hideChrome()
        dismissReturn()
        flush()
        loading?.cancel()
        currentUri = null
        resetPaging()
        mutableState.value = ReaderUiState.Idle
    }

    private fun resetPaging() {
        paginating?.cancel()
        lastRequest = null
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
        if (ready.scrollOnly || request == lastRequest) return
        lastRequest = request
        paginating?.cancel()
        mutablePaged.value = null
        firstDrawReported = false
        val started = nanoClock()
        requestedAt = started
        val from = mutableAnchor.value
        paginating =
            viewModelScope.launch(computeDispatcher) {
                val text = engine.prepare(request)
                val geometry = GeometryCalculator.compute(text.spec)
                val detailer = PageDetailer(ready.document, text.measurer, geometry)
                val cancel = CancellationSignal { !isActive }
                pagination.paginate(ready.contentHash, ready.document, text.measurer, geometry, from, cancel) { flow ->
                    if (!isActive) return@paginate
                    val layout = PagedLayout(request, geometry, text.config, flow, detailer)
                    perf.record(
                        "pagination.${layout.stage.name.lowercase()}",
                        (nanoClock() - started) / NANOS_PER_MILLI,
                    )
                    mutablePaged.value = layout
                }
            }
    }

    /** The surface drew its first spread for the current request: the "first spread" time of PRF-04. */
    public fun onFirstSpreadDrawn() {
        if (firstDrawReported) return
        firstDrawReported = true
        perf.record("firstSpreadDrawn", (nanoClock() - requestedAt) / NANOS_PER_MILLI)
    }

    /** Called when a turn or a scroll settles. Persists at most once per second; [flush] writes the rest (ANC-02). */
    public fun onAnchorSettled(anchor: Anchor) {
        mutableAnchor.value = anchor
        pending = anchor
        val last = lastSavedAt
        if (last == null || clock() - last >= SAVE_INTERVAL_MILLIS) flush()
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
