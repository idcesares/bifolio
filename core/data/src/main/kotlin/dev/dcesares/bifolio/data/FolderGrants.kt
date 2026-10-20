package dev.dcesares.bifolio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder

/** A file read through a granted folder. [mimeType] is what the provider reports, null when it reports none. */
public class OpenedFile(
    public val stream: InputStream,
    public val mimeType: String?,
)

/** What the folder grants need from the platform document provider (SAF). Read only. Blocking: call off main. */
public interface FolderDocuments {
    /**
     * Names from the root of the tree [treeUri] down to the document [documentUri] (file name last), asked from
     * the provider when the ids do not reveal them. Null when the document is not below the tree or not known.
     */
    public fun namesFromRoot(
        treeUri: String,
        documentUri: String,
    ): List<String>?

    /** Opens the file reached by walking [names] down from the root of [treeUri], or null when there is none. */
    public fun open(
        treeUri: String,
        names: List<String>,
    ): OpenedFile?
}

/**
 * A folder the reader may read images from, for one document. [base] is the folder names from [tree] down to the
 * folder of the document, the `base` of `ImageSources.classify` (empty when the document sits in the tree root).
 */
public data class FolderGrant(
    val tree: String,
    val base: List<String>,
)

private const val DEFAULT_MAX_TREES = 16
private const val DEFAULT_MAX_DISMISSED = 200

/**
 * Folder (SAF tree) grants for relative images (UI-14, SYN-08), over the same DataStore as the recents. A grant is a
 * persisted read permission on a tree URI, nothing more (PRV-04); this class remembers which trees were granted and
 * which documents the reader already asked about. It never writes to the folder.
 *
 * Which document a tree serves is derived from containment, not stored per document, so two documents in one folder
 * share a grant and the reader asks for a folder once (UI-14). Create exactly one instance per DataStore file.
 */
@Suppress("TooManyFunctions") // One small operation per grant event: find, locate, add, revoke, dismiss, open.
public class FolderGrantRepository(
    private val store: DataStore<Preferences>,
    private val grants: UriGrants,
    private val documents: FolderDocuments,
    private val maxTrees: Int = DEFAULT_MAX_TREES,
    private val maxDismissed: Int = DEFAULT_MAX_DISMISSED,
) {
    private val treesKey = stringPreferencesKey("folder_trees")
    private val dismissedKey = stringPreferencesKey("folder_dismissed")

    /** Every granted tree, newest first, for the grants list in settings (PRV-04). */
    public val trees: Flow<List<String>> = store.data.map { decode(it[treesKey].orEmpty()) }

    /**
     * The granted tree that holds [documentUri], or null. Trees whose permission was revoked outside the app are
     * forgotten on the way.
     */
    public suspend fun find(documentUri: String): FolderGrant? {
        val stored = trees.first()
        val stale = stored.filter { !grants.isHeld(it) }
        if (stale.isNotEmpty()) forget(stale.toSet(), release = false)
        for (tree in stored) {
            if (tree in stale) continue
            locate(tree, documentUri)?.let { return it }
        }
        return null
    }

    /**
     * Whether the tree [treeUri] holds [documentUri] and where the document sits in it. This is the check made
     * before a chosen folder is accepted: a folder that does not contain the document is not a grant for it.
     */
    public suspend fun locate(
        treeUri: String,
        documentUri: String,
    ): FolderGrant? {
        val tree = FolderPaths.treeOf(treeUri) ?: return null
        val doc = FolderPaths.documentOf(documentUri)
        val names =
            doc
                ?.takeIf { it.authority.equals(tree.authority, ignoreCase = true) }
                ?.let { FolderPaths.namesBelow(tree.id, it.id) }
                ?: askProvider(treeUri, documentUri)
        if (names.isNullOrEmpty()) return null
        return FolderGrant(treeUri, names.dropLast(1))
    }

    private suspend fun askProvider(
        treeUri: String,
        documentUri: String,
    ): List<String>? =
        withContext(Dispatchers.IO) { runCatching { documents.namesFromRoot(treeUri, documentUri) }.getOrNull() }

    /**
     * Accepts [treeUri] as a grant for [documentUri]: it must contain the document, and the provider must allow a
     * persistable read grant. Trees inside the new one are released, since it covers them. Null when refused.
     */
    public suspend fun add(
        treeUri: String,
        documentUri: String,
    ): FolderGrant? {
        val found = locate(treeUri, documentUri) ?: return null
        if (!grants.take(treeUri)) return null
        val newRef = FolderPaths.treeOf(treeUri)
        var evicted = emptyList<String>()
        store.edit { prefs ->
            val current = decode(prefs[treesKey].orEmpty())
            val covered =
                current.filter { other ->
                    val ref = FolderPaths.treeOf(other)
                    other != treeUri && newRef != null && ref != null && covers(newRef, ref)
                }
            val merged = listOf(treeUri) + current.filter { it != treeUri && it !in covered }
            evicted = covered + merged.drop(maxTrees)
            prefs[treesKey] = encode(merged.take(maxTrees))
        }
        evicted.filter { it != treeUri }.forEach(grants::release)
        return found
    }

    /** Forgets the tree and releases its permission (PRV-04). Documents that relied on it are asked again. */
    public suspend fun revoke(treeUri: String) {
        forget(setOf(treeUri), release = true)
    }

    /**
     * The reader asked about [documentUri] and the user said no: do not ask again for any document in its folder
     * (UI-14, once per folder). A document whose folder the id does not reveal is remembered by itself.
     */
    public suspend fun dismiss(documentUri: String) {
        val key = dismissKey(documentUri)
        store.edit { prefs ->
            val current = decode(prefs[dismissedKey].orEmpty())
            prefs[dismissedKey] = encode((current.filter { it != key } + key).takeLast(maxDismissed))
        }
    }

    public suspend fun isDismissed(documentUri: String): Boolean =
        dismissKey(documentUri) in decode(store.data.first()[dismissedKey].orEmpty())

    private fun dismissKey(documentUri: String): String = FolderPaths.folderOf(documentUri) ?: documentUri

    /** Opens the file at [segments] from the root of [folder]'s tree. Null if missing or unreadable. Blocking. */
    public fun open(
        folder: FolderGrant,
        segments: List<String>,
    ): OpenedFile? =
        try {
            documents.open(folder.tree, segments)
        } catch (_: SecurityException) {
            null
        }

    private suspend fun forget(
        removed: Set<String>,
        release: Boolean,
    ) {
        store.edit { prefs ->
            prefs[treesKey] = encode(decode(prefs[treesKey].orEmpty()).filter { it !in removed })
        }
        if (release) removed.forEach(grants::release)
    }

    private fun covers(
        outer: DocumentRef,
        inner: DocumentRef,
    ): Boolean =
        outer.authority.equals(inner.authority, ignoreCase = true) &&
            FolderPaths.namesBelow(outer.id, inner.id) != null

    private fun encode(list: List<String>): String = list.joinToString("\n") { URLEncoder.encode(it, "UTF-8") }

    private fun decode(text: String): List<String> =
        text
            .lineSequence()
            .filter { it.isNotEmpty() }
            .map { URLDecoder.decode(it, "UTF-8") }
            .toList()
}
