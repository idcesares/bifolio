package dev.dcesares.bifolio.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

private const val AUTH = "com.android.externalstorage.documents"

private fun tree(id: String) = "content://$AUTH/tree/${java.net.URLEncoder.encode(id, "UTF-8")}"

private fun doc(id: String) = "content://$AUTH/document/${java.net.URLEncoder.encode(id, "UTF-8")}"

private class Grants(
    val refuse: Set<String> = emptySet(),
) : UriGrants {
    val held = linkedSetOf<String>()
    val released = mutableListOf<String>()

    override fun take(uri: String): Boolean = (uri !in refuse).also { if (it) held += uri }

    override fun release(uri: String) {
        held -= uri
        released += uri
    }

    override fun isHeld(uri: String): Boolean = uri in held
}

private class Provider(
    var names: List<String>? = null,
    val files: Map<List<String>, OpenedFile> = emptyMap(),
) : FolderDocuments {
    var asked = 0

    override fun namesFromRoot(
        treeUri: String,
        documentUri: String,
    ): List<String>? {
        asked++
        return names
    }

    override fun open(
        treeUri: String,
        names: List<String>,
    ): OpenedFile? = files[names]
}

/** UI-14 (folder grant, no repeat prompt), PRV-04 (grants stored and revocable), SYN-08. */
class FolderGrantRepositoryTest {
    private fun repo(
        grants: Grants = Grants(),
        provider: Provider = Provider(),
    ) = FolderGrantRepository(InMemoryPreferences(), grants, provider)

    @Test
    fun acceptsATreeThatHoldsTheDocumentAndReportsTheBase() =
        runTest {
            val grants = Grants()
            val repo = repo(grants)
            val got = repo.add(tree("primary:Docs"), doc("primary:Docs/notes/a.md"))
            assertEquals(FolderGrant(tree("primary:Docs"), listOf("notes")), got)
            assertTrue(grants.isHeld(tree("primary:Docs")))
            assertEquals(listOf(tree("primary:Docs")), repo.trees.first())
        }

    @Test
    fun refusesATreeThatDoesNotHoldTheDocumentAndTakesNoPermission() =
        runTest {
            val grants = Grants()
            val repo = repo(grants)
            assertNull(repo.add(tree("primary:Other"), doc("primary:Docs/a.md")))
            assertTrue(grants.held.isEmpty())
            assertEquals(emptyList<String>(), repo.trees.first())
        }

    @Test
    fun aSiblingFolderWithTheSamePrefixIsRefused() =
        runTest {
            assertNull(repo().add(tree("primary:Docs"), doc("primary:Docs2/a.md")))
        }

    @Test
    fun asksTheProviderWhenIdsRevealNothing() =
        runTest {
            val provider = Provider(names = listOf("sub", "a.md"))
            val got = repo(provider = provider).add(tree("tree-root"), doc("msf:42"))
            assertEquals(FolderGrant(tree("tree-root"), listOf("sub")), got)
            assertEquals(1, provider.asked)
        }

    @Test
    fun aProviderThatKnowsNothingRefuses() =
        runTest {
            assertNull(repo(provider = Provider(names = null)).add(tree("tree-root"), doc("msf:42")))
        }

    @Test
    fun whenThePlatformRefusesThePermissionNothingIsStored() =
        runTest {
            val t = tree("primary:Docs")
            val repo = repo(Grants(refuse = setOf(t)))
            assertNull(repo.add(t, doc("primary:Docs/a.md")))
            assertEquals(emptyList<String>(), repo.trees.first())
        }

    @Test
    fun anotherDocumentInTheSameFolderIsFoundWithoutAsking() =
        runTest {
            val repo = repo()
            repo.add(tree("primary:Docs"), doc("primary:Docs/a.md"))
            val found = repo.find(doc("primary:Docs/deeper/b.md"))
            assertEquals(FolderGrant(tree("primary:Docs"), listOf("deeper")), found)
            assertNull(repo.find(doc("primary:Elsewhere/c.md")))
        }

    @Test
    fun aWiderFolderReplacesTheOnesInsideItAndReleasesThem() =
        runTest {
            val grants = Grants()
            val repo = repo(grants)
            repo.add(tree("primary:Docs/notes"), doc("primary:Docs/notes/a.md"))
            repo.add(tree("primary:Docs/other"), doc("primary:Docs/other/b.md"))
            repo.add(tree("primary:Docs"), doc("primary:Docs/notes/a.md"))
            assertEquals(listOf(tree("primary:Docs")), repo.trees.first())
            assertEquals(setOf(tree("primary:Docs/notes"), tree("primary:Docs/other")), grants.released.toSet())
            assertEquals(setOf(tree("primary:Docs")), grants.held)
        }

    @Test
    fun grantingTheSameTreeAgainDoesNotReleaseIt() =
        runTest {
            val grants = Grants()
            val repo = repo(grants)
            repo.add(tree("primary:Docs"), doc("primary:Docs/a.md"))
            repo.add(tree("primary:Docs"), doc("primary:Docs/b.md"))
            assertEquals(listOf(tree("primary:Docs")), repo.trees.first())
            assertTrue(grants.released.isEmpty())
            assertTrue(grants.isHeld(tree("primary:Docs")))
        }

    @Test
    fun oldestTreesAreReleasedBeyondTheCap() =
        runTest {
            val grants = Grants()
            val repo = FolderGrantRepository(InMemoryPreferences(), grants, Provider(), maxTrees = 2)
            for (n in 1..3) repo.add(tree("primary:F$n"), doc("primary:F$n/a.md"))
            assertEquals(listOf(tree("primary:F3"), tree("primary:F2")), repo.trees.first())
            assertEquals(listOf(tree("primary:F1")), grants.released)
        }

    @Test
    fun aPermissionRevokedOutsideTheAppIsForgotten() =
        runTest {
            val grants = Grants()
            val repo = repo(grants)
            repo.add(tree("primary:Docs"), doc("primary:Docs/a.md"))
            grants.held.clear()
            assertNull(repo.find(doc("primary:Docs/a.md")))
            assertEquals(emptyList<String>(), repo.trees.first())
        }

    @Test
    fun revokeReleasesAndForgets() =
        runTest {
            val grants = Grants()
            val repo = repo(grants)
            repo.add(tree("primary:Docs"), doc("primary:Docs/a.md"))
            repo.revoke(tree("primary:Docs"))
            assertEquals(emptyList<String>(), repo.trees.first())
            assertEquals(listOf(tree("primary:Docs")), grants.released)
            assertNull(repo.find(doc("primary:Docs/a.md")))
        }

    @Test
    fun dismissIsPerFolderAndRemembered() =
        runTest {
            val repo = repo()
            assertFalse(repo.isDismissed(doc("primary:Docs/a.md")))
            repo.dismiss(doc("primary:Docs/a.md"))
            assertTrue(repo.isDismissed(doc("primary:Docs/a.md")))
            assertTrue(repo.isDismissed(doc("primary:Docs/b.md")), "a sibling in the same folder is not asked again")
            assertFalse(repo.isDismissed(doc("primary:Other/b.md")), "another folder is")
            assertFalse(repo.isDismissed(doc("primary:Docs/sub/b.md")), "a subfolder is another folder")
            assertFalse(repo.isDismissed(doc("primary:Docs2/a.md")), "a sibling folder sharing the prefix is another")
        }

    @Test
    fun aDocumentWhoseFolderIsNotRevealedIsDismissedAlone() =
        runTest {
            val repo = repo()
            val opaque = "content://downloads/document/msf%3A42"
            repo.dismiss(opaque)
            assertTrue(repo.isDismissed(opaque))
            assertFalse(repo.isDismissed("content://downloads/document/msf%3A43"))
        }

    @Test
    fun openWalksTheSegmentsFromTheRoot() =
        runTest {
            val file = OpenedFile(ByteArrayInputStream(byteArrayOf(1)), "image/png")
            val repo = repo(provider = Provider(files = mapOf(listOf("img", "a.png") to file)))
            val folder = FolderGrant(tree("primary:Docs"), emptyList())
            assertEquals(file, repo.open(folder, listOf("img", "a.png")))
            assertNull(repo.open(folder, listOf("img", "missing.png")))
        }
}
