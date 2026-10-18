package dev.dcesares.bifolio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

    private val themeKey = stringPreferencesKey("appearance_theme")
    private val sizeKey = intPreferencesKey("appearance_size_sp")
    private val lineHeightKey = floatPreferencesKey("appearance_line_height")
    private val marginKey = floatPreferencesKey("appearance_margin_scale")
    private val columnsKey = intPreferencesKey("appearance_columns")
    private val justifyKey = booleanPreferencesKey("appearance_justify")
    private val hyphenKey = booleanPreferencesKey("appearance_hyphenate")
    private val turnKey = stringPreferencesKey("appearance_turn")
    private val fontKey = stringPreferencesKey("appearance_font")
    private val hairlineKey = booleanPreferencesKey("appearance_hairline")
    private val reduceMotionKey = booleanPreferencesKey("appearance_reduce_motion")

    /** The global appearance profile (UI-10), always [AppearanceProfile.normalized]. */
    public val appearance: Flow<AppearanceProfile> =
        store.data.map { it.toProfile() }.distinctUntilChanged()

    /** Applies [change] to the stored profile and writes the normalized result. */
    public suspend fun updateAppearance(change: (AppearanceProfile) -> AppearanceProfile) {
        store.edit { prefs ->
            val next = change(prefs.toProfile()).normalized()
            prefs.putOrRemove(themeKey, next.theme?.name)
            prefs[sizeKey] = next.bodySp
            prefs[lineHeightKey] = next.lineHeight
            prefs[marginKey] = next.marginScale
            prefs.putOrRemove(columnsKey, next.columns)
            prefs[justifyKey] = next.justify
            prefs[hyphenKey] = next.hyphenate
            prefs[turnKey] = next.turn.name
            prefs[fontKey] = next.font.name
            prefs[hairlineKey] = next.hairline
            prefs[reduceMotionKey] = next.reduceMotion
        }
    }

    private fun Preferences.toProfile(): AppearanceProfile {
        val base = AppearanceProfile()
        return AppearanceProfile(
            theme = ThemeChoice.entries.firstOrNull { it.name == this[themeKey] },
            bodySp = this[sizeKey] ?: base.bodySp,
            lineHeight = this[lineHeightKey] ?: base.lineHeight,
            marginScale = this[marginKey] ?: base.marginScale,
            columns = this[columnsKey],
            justify = this[justifyKey] ?: base.justify,
            hyphenate = this[hyphenKey] ?: base.hyphenate,
            turn = TurnChoice.entries.firstOrNull { it.name == this[turnKey] } ?: base.turn,
            font = FontChoice.entries.firstOrNull { it.name == this[fontKey] } ?: base.font,
            hairline = this[hairlineKey] ?: base.hairline,
            reduceMotion = this[reduceMotionKey] ?: base.reduceMotion,
        ).normalized()
    }

    private fun <T> MutablePreferences.putOrRemove(
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) remove(key) else this[key] = value
    }
}
