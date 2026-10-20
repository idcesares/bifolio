package dev.dcesares.bifolio.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore

private val Context.bifolioDataStore by preferencesDataStore(name = "bifolio")

/** [UriGrants] backed by the content resolver's persisted permissions. */
public class AndroidUriGrants(
    private val resolver: ContentResolver,
) : UriGrants {
    override fun take(uri: String): Boolean =
        try {
            resolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

    override fun release(uri: String) {
        try {
            resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Nothing was held for this URI.
        }
    }

    override fun isHeld(uri: String): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission }
}

/** The app-wide repository. Call once and keep the instance (one DataStore per file). */
public fun createRecentsRepository(context: Context): RecentsRepository {
    val app = context.applicationContext
    return RecentsRepository(app.bifolioDataStore, AndroidUriGrants(app.contentResolver))
}

/** Folder grants for relative images (UI-14), on the same DataStore file as [createRecentsRepository]. */
public fun createFolderGrants(context: Context): FolderGrantRepository {
    val app = context.applicationContext
    return FolderGrantRepository(
        app.bifolioDataStore,
        AndroidUriGrants(app.contentResolver),
        AndroidFolderDocuments(app.contentResolver),
    )
}

/** The app-wide reading profile, on the same DataStore file as [createRecentsRepository]. */
public fun createReadingPreferences(context: Context): ReadingPreferences =
    ReadingPreferences(context.applicationContext.bifolioDataStore)
