package dev.dcesares.bifolio.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dcesares.bifolio.data.RecentDocument

private val PickerMimeTypes =
    arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")

/**
 * Library wired to the system picker (DOC-02). [onDocumentPicked] receives a URI from `ACTION_OPEN_DOCUMENT`,
 * for which the caller should take a persistable grant.
 */
@Composable
public fun LibraryRoute(
    viewModel: LibraryViewModel,
    onDocumentPicked: (Uri) -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onDocumentPicked(uri)
        }
    val now = remember(state.recents) { System.currentTimeMillis() }
    LibraryScreen(
        state = state,
        nowMillis = now,
        onOpenDocument = { launcher.launch(PickerMimeTypes) },
        onOpenRecent = onOpenRecent,
        onRemoveRecent = viewModel::remove,
        onDismissNotice = viewModel::dismissNotice,
        modifier = modifier,
    )
}
