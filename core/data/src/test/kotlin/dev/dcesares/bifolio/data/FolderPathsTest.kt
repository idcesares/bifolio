package dev.dcesares.bifolio.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** UI-14 containment and picker start, decided from the URI text alone. */
class FolderPathsTest {
    private val storage = "com.android.externalstorage.documents"

    @Test
    fun readsDocumentAndTreeIds() {
        val doc = FolderPaths.documentOf("content://$storage/document/primary%3ADocs%2Fnotes%2Fa.md")
        assertEquals(DocumentRef(storage, "primary:Docs/notes/a.md"), doc)
        val tree = FolderPaths.treeOf("content://$storage/tree/primary%3ADocs")
        assertEquals(DocumentRef(storage, "primary:Docs"), tree)
        val both = "content://$storage/tree/primary%3ADocs/document/primary%3ADocs%2Fa.md"
        assertEquals("primary:Docs/a.md", FolderPaths.documentOf(both)?.id)
        assertEquals("primary:Docs", FolderPaths.treeOf(both)?.id)
    }

    @Test
    fun notAContentUriGivesNothing() {
        assertNull(FolderPaths.documentOf("file:///sdcard/a.md"))
        assertNull(FolderPaths.treeOf("https://example.com/tree/x"))
        assertNull(FolderPaths.documentOf("content://$storage/tree/primary%3ADocs"))
        assertNull(FolderPaths.treeOf("content://$storage/document/primary%3ADocs"))
    }

    @Test
    fun plusInAnIdIsKept() {
        assertEquals(
            "primary:C++/a.md",
            FolderPaths.documentOf("content://$storage/document/primary%3AC%2B%2B%2Fa.md")?.id,
        )
    }

    @Test
    fun namesBelowATree() {
        assertEquals(listOf("notes", "a.md"), FolderPaths.namesBelow("primary:Docs", "primary:Docs/notes/a.md"))
        assertEquals(listOf("a.md"), FolderPaths.namesBelow("primary:Docs", "primary:Docs/a.md"))
    }

    @Test
    fun theRootOfAVolumeEndsWithAColon() {
        assertEquals(listOf("Docs", "a.md"), FolderPaths.namesBelow("primary:", "primary:Docs/a.md"))
    }

    @Test
    fun aSiblingWithTheSamePrefixIsNotInside() {
        assertNull(FolderPaths.namesBelow("primary:Docs", "primary:Docs2/a.md"))
    }

    @Test
    fun theTreeItselfAndOtherVolumesAreNotInside() {
        assertNull(FolderPaths.namesBelow("primary:Docs", "primary:Docs"))
        assertNull(FolderPaths.namesBelow("primary:Docs", "1234-ABCD:Docs/a.md"))
        assertNull(FolderPaths.namesBelow("primary:Docs", "primary:Docs//a.md"))
    }

    @Test
    fun pickerOpensAtTheFolderOfTheDocument() {
        val start = FolderPaths.pickerStart("content://$storage/document/primary%3ADocs%2Fmy%20notes%2Fa.md")
        assertEquals("content://$storage/document/primary%3ADocs%2Fmy%20notes", start)
    }

    @Test
    fun generatedFoldersAndNames() {
        val roots = listOf("primary:Docs", "primary:", "1234-ABCD:Books", "primary:C++", "primary:a b")
        val paths = listOf("a.md", "n/a.md", "n/m/a.md", "n m/ü é.md", "100%/a.md")
        for (root in roots) {
            val sep = if (root.endsWith(":")) "" else "/"
            for (path in paths) {
                val id = "$root$sep$path"
                assertEquals(path.split('/'), FolderPaths.namesBelow(root, id), id)
                // Trailing slash on the tree id is the same tree.
                assertEquals(path.split('/'), FolderPaths.namesBelow("$root/".replace(":/", ":"), id), id)
                // A sibling that only shares the prefix is not below.
                if (!root.endsWith(":")) assertNull(FolderPaths.namesBelow(root, "${root}2/$path"), id)
                // The folder of a document is the parent, and its own folder is the same string, percent-encoded.
                val uri = "content://$storage/document/${java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")}"
                val folder = FolderPaths.folderOf(uri)
                val parentId = id.substringBeforeLast('/', root)
                assertEquals(
                    FolderPaths.documentOf(folder!!)?.id,
                    if ('/' in
                        id
                    ) {
                        parentId
                    } else {
                        root.substringBefore(':') + ":"
                    },
                    id,
                )
                assertEquals(folder, FolderPaths.pickerStart(uri), id)
            }
        }
    }

    @Test
    fun noNamesForTheTreeItselfOrAnEmptySegment() {
        assertNull(FolderPaths.namesBelow("primary:Docs", "primary:Docs"))
        assertNull(FolderPaths.namesBelow("primary:Docs", "primary:Docs/"))
        assertNull(FolderPaths.namesBelow("primary:Docs", "primary:Docs/a//b.md"))
        assertNull(FolderPaths.namesBelow("primary:", "primary:"))
    }

    @Test
    fun pickerFallsBackToTheDocumentWhenTheIdHasNoFolder() {
        val start = FolderPaths.pickerStart("content://downloads/document/msf%3A42")
        assertEquals("content://downloads/document/msf%3A42", start)
        assertNull(FolderPaths.pickerStart("file:///sdcard/a.md"))
    }
}
