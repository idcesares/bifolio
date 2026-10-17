package dev.dcesares.bifolio.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dev.dcesares.bifolio.parse.LoadResult
import dev.dcesares.bifolio.parse.MarkdownLoader
import dev.dcesares.bifolio.parse.SizeClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
private const val NANOS_PER_MILLI = 1_000_000L

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
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    public val state: StateFlow<ReaderUiState> = mutableState

    private val mutableAnchor = MutableStateFlow(Anchor(0, 0))

    /** Where the reader is. Changes on open and when a turn or a scroll settles (ANC-02). */
    public val anchor: StateFlow<Anchor> = mutableAnchor

    private val mutablePaged = MutableStateFlow<PagedLayout?>(null)

    /** Latest pagination snapshot for the last [paginate] request, null until the first one arrives. */
    public val paged: StateFlow<PagedLayout?> = mutablePaged

    /** The mode shown: the global preference, except that files too large for pages always scroll (DOC-05). */
    public val mode: StateFlow<ReadingMode> =
        combine(preferences.mode, mutableState) { preferred, current ->
            if ((current as? ReaderUiState.Ready)?.scrollOnly == true) ReadingMode.SCROLL else preferred
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ReadingMode.PAGED)

    private val pagination = PaginationService(LayoutCache())
    private var paginating: Job? = null
    private var lastRequest: PageRequest? = null
    private var requestedAt = 0L
    private var firstDrawReported = false

    private var loading: Job? = null
    private var currentUri: String? = null
    private var lastSavedAt: Long? = null
    private var pending: Anchor? = null

    public fun open(request: OpenRequest) {
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
            RecentDocument(request.uri, title, name, clock(), progressOf(document, anchor), hash),
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
     * carries over, so the same passage stays in view (ANC-04).
     */
    public fun toggleMode() {
        if ((mutableState.value as? ReaderUiState.Ready)?.scrollOnly != false) return
        val next = if (mode.value == ReadingMode.PAGED) ReadingMode.SCROLL else ReadingMode.PAGED
        viewModelScope.launch { preferences.setMode(next) }
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
