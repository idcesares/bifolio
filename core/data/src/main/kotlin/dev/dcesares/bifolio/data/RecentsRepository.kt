package dev.dcesares.bifolio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Persistable read grants on document URIs (DOC-02). */
public interface UriGrants {
    /** Takes a persistable read grant. Returns false when the provider does not allow it. */
    public fun take(uri: String): Boolean

    public fun release(uri: String)

    public fun isHeld(uri: String): Boolean
}

private const val DEFAULT_MAX_RECENTS = 50
private const val DEFAULT_MAX_ANCHORS = 200

/**
 * Recents (UI-01), grants (DOC-02) and the reading anchor per document (ANC-02) over DataStore.
 * Create exactly one instance per DataStore file.
 */
public class RecentsRepository(
    private val store: DataStore<Preferences>,
    private val grants: UriGrants,
    private val maxRecents: Int = DEFAULT_MAX_RECENTS,
    private val maxAnchors: Int = DEFAULT_MAX_ANCHORS,
) {
    private val recentsKey = stringPreferencesKey("recents")
    private val anchorsKey = stringPreferencesKey("anchors")

    /** Most recently opened first. */
    public val recents: Flow<List<RecentDocument>> =
        store.data.map { RecentsCodec.decodeRecents(it[recentsKey].orEmpty()) }

    /** Inserts or replaces the entry for [doc].uri, moves it to the front and evicts beyond the cap. */
    public suspend fun upsert(doc: RecentDocument) {
        var evicted = emptyList<RecentDocument>()
        store.edit { prefs ->
            val current = RecentsCodec.decodeRecents(prefs[recentsKey].orEmpty())
            val merged = (listOf(doc) + current.filter { it.uri != doc.uri }).sortedByDescending { it.lastOpenedMillis }
            evicted = merged.drop(maxRecents)
            prefs[recentsKey] = RecentsCodec.encodeRecents(merged.take(maxRecents))
        }
        evicted.filter { it.uri != doc.uri }.forEach { grants.release(it.uri) }
    }

    public suspend fun updateProgress(
        uri: String,
        progress: Float,
        at: Long,
    ) {
        store.edit { prefs ->
            val updated =
                RecentsCodec.decodeRecents(prefs[recentsKey].orEmpty()).map {
                    if (it.uri == uri) it.copy(progress = progress.coerceIn(0f, 1f), lastOpenedMillis = at) else it
                }
            prefs[recentsKey] = RecentsCodec.encodeRecents(updated.sortedByDescending { it.lastOpenedMillis })
        }
    }

    /** Flags the recent as unavailable (UI-16). Unknown uris are ignored; a later [upsert] clears the flag. */
    public suspend fun markUnavailable(uri: String) {
        store.edit { prefs ->
            val current = RecentsCodec.decodeRecents(prefs[recentsKey].orEmpty())
            if (current.none { it.uri == uri }) return@edit
            prefs[recentsKey] =
                RecentsCodec.encodeRecents(current.map { if (it.uri == uri) it.copy(unavailable = true) else it })
        }
    }

    /** Removes the entry and releases its grant. The file itself is never touched. */
    public suspend fun remove(uri: String) {
        store.edit { prefs ->
            val kept = RecentsCodec.decodeRecents(prefs[recentsKey].orEmpty()).filter { it.uri != uri }
            prefs[recentsKey] = RecentsCodec.encodeRecents(kept)
        }
        grants.release(uri)
    }

    /** Takes a persistable grant for [uri]. False when the provider refused (the file still opens this session). */
    public fun persistGrant(uri: String): Boolean = grants.take(uri)

    public fun hasGrant(uri: String): Boolean = grants.isHeld(uri)

    public suspend fun anchorFor(uri: String): Anchor? =
        RecentsCodec.decodeAnchors(readAnchors()).firstOrNull { it.first == uri }?.second

    /** Stores the anchor for the document [uri] (ANC-02). The oldest saved anchors are dropped beyond the cap. */
    public suspend fun saveAnchor(
        uri: String,
        anchor: Anchor,
    ) {
        store.edit { prefs ->
            val current = RecentsCodec.decodeAnchors(prefs[anchorsKey].orEmpty())
            val next = current.filter { it.first != uri } + (uri to anchor)
            prefs[anchorsKey] = RecentsCodec.encodeAnchors(next.takeLast(maxAnchors))
        }
    }

    private suspend fun readAnchors(): String = store.data.first()[anchorsKey].orEmpty()
}
