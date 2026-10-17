package dev.dcesares.bifolio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File

private class FakeGrants(
    val refuse: Set<String> = emptySet(),
) : UriGrants {
    val held = linkedSetOf<String>()

    override fun take(uri: String): Boolean = (uri !in refuse).also { if (it) held += uri }

    override fun release(uri: String) {
        held -= uri
    }

    override fun isHeld(uri: String): Boolean = uri in held
}

/** DOC-02 (persistable grants), UI-01 (recents), ANC-02 (anchor per document). */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentsRepositoryTest {
    @TempDir
    lateinit var dir: File

    private fun store(): DataStore<Preferences> = InMemoryPreferences()

    private fun doc(
        n: Int,
        at: Long = n.toLong(),
    ) = RecentDocument("content://docs/$n", "Title $n", "file$n.md", at, 0f, "hash$n")

    @Test
    fun emptyAtStart() =
        runTest {
            val repo = RecentsRepository(store(), FakeGrants())
            assertEquals(emptyList<RecentDocument>(), repo.recents.first())
        }

    @Test
    fun upsertOrdersMostRecentFirstAndDeduplicatesByUri() =
        runTest {
            val repo = RecentsRepository(store(), FakeGrants())
            repo.upsert(doc(1, at = 10))
            repo.upsert(doc(2, at = 20))
            repo.upsert(doc(1, at = 30).copy(title = "Renamed"))
            val list = repo.recents.first()
            assertEquals(listOf("content://docs/1", "content://docs/2"), list.map { it.uri })
            assertEquals("Renamed", list.first().title)
        }

    @Test
    fun removeDropsEntryAndReleasesGrant() =
        runTest {
            val grants = FakeGrants()
            val repo = RecentsRepository(store(), grants)
            grants.take("content://docs/1")
            repo.upsert(doc(1))
            repo.remove("content://docs/1")
            assertTrue(repo.recents.first().isEmpty())
            assertFalse(grants.isHeld("content://docs/1"))
        }

    @Test
    fun capEvictsOldestAndReleasesItsGrant() =
        runTest {
            val grants = FakeGrants()
            val repo = RecentsRepository(store(), grants, maxRecents = 3)
            (1..4).forEach {
                grants.take("content://docs/$it")
                repo.upsert(doc(it, at = it.toLong()))
            }
            assertEquals(listOf(4, 3, 2), repo.recents.first().map { it.uri.substringAfterLast('/').toInt() })
            assertFalse(grants.isHeld("content://docs/1"))
            assertTrue(grants.isHeld("content://docs/2"))
        }

    @Test
    fun grantIsTakenOnlyWhenRequestedAndRefusalIsReported() =
        runTest {
            val grants = FakeGrants(refuse = setOf("content://docs/9"))
            val repo = RecentsRepository(store(), grants)
            assertTrue(repo.persistGrant("content://docs/1"))
            assertFalse(repo.persistGrant("content://docs/9"))
            assertTrue(grants.isHeld("content://docs/1"))
        }

    @Test
    fun progressUpdateKeepsOtherFields() =
        runTest {
            val repo = RecentsRepository(store(), FakeGrants())
            repo.upsert(doc(1, at = 5))
            repo.updateProgress("content://docs/1", 0.42f, at = 99)
            val r = repo.recents.first().single()
            assertEquals(0.42f, r.progress)
            assertEquals(99L, r.lastOpenedMillis)
            assertEquals("Title 1", r.title)
        }

    @Test
    fun anchorRoundTripsByContentHash() =
        runTest {
            val repo = RecentsRepository(store(), FakeGrants())
            assertNull(repo.anchorFor("h1"))
            repo.saveAnchor("h1", Anchor(7, 12))
            repo.saveAnchor("h2", Anchor(1, 0))
            assertEquals(Anchor(7, 12), repo.anchorFor("h1"))
            repo.saveAnchor("h1", Anchor(9, 3))
            assertEquals(Anchor(9, 3), repo.anchorFor("h1"))
        }

    @Test
    fun anchorsAreBoundedLeastRecentlySaved() =
        runTest {
            val repo = RecentsRepository(store(), FakeGrants(), maxAnchors = 2)
            repo.saveAnchor("a", Anchor(1, 0))
            repo.saveAnchor("b", Anchor(2, 0))
            repo.saveAnchor("c", Anchor(3, 0))
            assertNull(repo.anchorFor("a"))
            assertEquals(Anchor(3, 0), repo.anchorFor("c"))
        }

    // File.renameTo cannot replace an existing file on Windows, which DataStore's file storage relies on.
    // The test runs on Linux and macOS (CI); on Windows the codec tests cover the persisted format.
    @DisabledOnOs(OS.WINDOWS)
    @Test
    fun dataSurvivesANewRepositoryOnTheSameFile() =
        runTest {
            val file = File(dir, "shared.preferences_pb")
            val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
            val first = PreferenceDataStoreFactory.create(scope = scope) { file }
            RecentsRepository(first, FakeGrants()).apply {
                upsert(doc(1))
                saveAnchor("hash1", Anchor(4, 2))
            }
            scope.coroutineContext[Job]!!.cancel()
            val second =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
                ) { file }
            val repo = RecentsRepository(second, FakeGrants())
            assertEquals(1, repo.recents.first().size)
            assertEquals(Anchor(4, 2), repo.anchorFor("hash1"))
        }
}
