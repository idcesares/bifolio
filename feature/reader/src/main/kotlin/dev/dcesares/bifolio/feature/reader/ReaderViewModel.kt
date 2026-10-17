package dev.dcesares.bifolio.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dcesares.bifolio.data.ContentHash
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.LoadResult
import dev.dcesares.bifolio.parse.MarkdownLoader
import dev.dcesares.bifolio.parse.SizeClass
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val MarkdownExtensions = listOf(".md", ".markdown", ".mdown", ".mkd")
private const val OCTET_STREAM = "application/octet-stream"
private const val SAVE_INTERVAL_MILLIS = 1_000L

/**
 * Opens documents and holds the loaded [Document] across configuration changes.
 * Parsing and hashing run on [computeDispatcher] (DOC-06); the main thread only publishes state.
 */
public class ReaderViewModel(
    private val reader: DocumentReader,
    private val repository: RecentsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    public val state: StateFlow<ReaderUiState> = mutableState

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
        loading = viewModelScope.launch { mutableState.value = load(request) }
    }

    /** Back to the library: forgets the loaded document. */
    public fun close() {
        flush()
        loading?.cancel()
        currentUri = null
        mutableState.value = ReaderUiState.Idle
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
        val anchor = restoreAnchor(document, repository.anchorFor(hash))
        val title = document.title?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.', name)
        repository.upsert(
            RecentDocument(request.uri, title, name, clock(), progressOf(document, anchor), hash),
        )
        currentUri = request.uri
        val notices =
            buildSet {
                if (loaded.sizeClass == SizeClass.SCROLL_ONLY) add(ReaderNotice.ScrollOnlyLargeFile)
                if (loaded.encodingWarning) add(ReaderNotice.Windows1252Fallback)
            }
        return ReaderUiState.Ready(document, title, hash, notices, anchor)
    }

    /** Called when scrolling settles. Persists at most once per second; [flush] writes the rest (ANC-02). */
    public fun onAnchorSettled(anchor: Anchor) {
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
            repository.saveAnchor(ready.contentHash, anchor)
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
