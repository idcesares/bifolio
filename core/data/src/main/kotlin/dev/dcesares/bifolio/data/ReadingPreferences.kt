package dev.dcesares.bifolio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How the reader shows a document (SCO-03). */
public enum class ReadingMode { PAGED, SCROLL }

/**
 * Global reading profile (decision of the owner: one profile for every document). Today it holds the reading
 * mode; the appearance settings join it in M4. Create exactly one instance per DataStore file.
 */
public class ReadingPreferences(
    private val store: DataStore<Preferences>,
) {
    private val modeKey = stringPreferencesKey("reading_mode")
    private val volumeKey = booleanPreferencesKey("volume_keys_turn_pages")

    /** The stored mode, [ReadingMode.PAGED] until the reader changes it. */
    public val mode: Flow<ReadingMode> =
        store.data
            .map { prefs -> ReadingMode.entries.firstOrNull { it.name == prefs[modeKey] } ?: ReadingMode.PAGED }
            .distinctUntilChanged()

    public suspend fun setMode(mode: ReadingMode) {
        store.edit { it[modeKey] = mode.name }
    }

    /** Whether the volume keys turn pages (NAV-04). Off until the reader enables it. */
    public val volumeKeysTurn: Flow<Boolean> =
        store.data.map { prefs -> prefs[volumeKey] ?: false }.distinctUntilChanged()

    public suspend fun setVolumeKeysTurn(enabled: Boolean) {
        store.edit { it[volumeKey] = enabled }
    }
}
