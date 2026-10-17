package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document

/** Quiet notices shown above the text (DOC-03, DOC-05). */
public enum class ReaderNotice { ScrollOnlyLargeFile, Windows1252Fallback }

/** Why a document did not open. */
public enum class FailureReason { NotMarkdown, TooLarge, Unreadable }

/** How a document should be opened. [mimeType] is the type the caller declared, null for the picker. */
public data class OpenRequest(
    val uri: String,
    val mimeType: String?,
    /** Keep read access across restarts (picker results and grants offered with the intent). */
    val persistGrant: Boolean,
)

public sealed interface ReaderUiState {
    public data object Idle : ReaderUiState

    public data object Loading : ReaderUiState

    public data class Failed(
        val reason: FailureReason,
    ) : ReaderUiState

    public class Ready(
        public val document: Document,
        public val title: String,
        public val contentHash: String,
        public val notices: Set<ReaderNotice>,
        /** Where to resume: the anchor stored for this content, clamped to the document. */
        public val initialAnchor: Anchor,
        /** Too large for pages: always scroll, whatever the reading mode says (DOC-05). */
        public val scrollOnly: Boolean = false,
    ) : ReaderUiState
}
