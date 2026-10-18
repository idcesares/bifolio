package dev.dcesares.bifolio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.data.RecentsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

public class LibraryViewModel(
    private val repository: RecentsRepository,
) : ViewModel() {
    private val notice = MutableStateFlow<LibraryNotice?>(null)

    public val state: StateFlow<LibraryUiState> =
        combine(repository.recents, notice) { recents, n -> LibraryUiState(recents, n) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LibraryUiState())

    public fun remove(recent: RecentDocument) {
        viewModelScope.launch { repository.remove(recent.uri) }
    }

    /** The recent for [uri] could not be opened: dim it and keep it until removed (UI-16). */
    public fun markUnavailable(uri: String) {
        viewModelScope.launch { repository.markUnavailable(uri) }
    }

    public fun showNotice(value: LibraryNotice) {
        notice.value = value
    }

    public fun dismissNotice() {
        notice.value = null
    }
}
