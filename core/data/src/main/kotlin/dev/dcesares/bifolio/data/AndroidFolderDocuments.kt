package dev.dcesares.bifolio.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * [FolderDocuments] over `DocumentsContract` (no androidx.documentfile). Everything is a read: queries and
 * `openInputStream`. Walking by display name costs one child query per folder level, which is fine for the few
 * images of a document, and works with providers whose ids reveal nothing.
 */
@Suppress("ReturnCount") // Each early return is a distinct not-found case of the provider walk.
public class AndroidFolderDocuments(
    private val resolver: ContentResolver,
) : FolderDocuments {
    override fun namesFromRoot(
        treeUri: String,
        documentUri: String,
    ): List<String>? {
        val tree = Uri.parse(treeUri)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val docId = FolderPaths.documentOf(documentUri)?.id ?: return null
        val inTree = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        val ids = DocumentsContract.findDocumentPath(resolver, inTree)?.path ?: return null
        val from = ids.indexOf(rootId)
        if (from < 0 || from == ids.lastIndex) return null
        return ids.drop(from + 1).map { displayName(tree, it) ?: return null }
    }

    override fun open(
        treeUri: String,
        names: List<String>,
    ): OpenedFile? {
        if (names.isEmpty()) return null
        val tree = Uri.parse(treeUri)
        var parent = DocumentsContract.getTreeDocumentId(tree)
        var mime: String? = null
        for ((i, name) in names.withIndex()) {
            val child = child(tree, parent, name) ?: return null
            if (i < names.lastIndex && child.mime != DocumentsContract.Document.MIME_TYPE_DIR) return null
            parent = child.id
            mime = child.mime
        }
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, parent)
        val stream = resolver.openInputStream(uri) ?: return null
        return OpenedFile(stream, mime)
    }

    private class Child(
        val id: String,
        val mime: String?,
    )

    private fun child(
        tree: Uri,
        parentId: String,
        name: String,
    ): Child? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        return resolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name) return@use Child(c.getString(0), c.getString(2))
            }
            null
        }
    }

    private fun displayName(
        tree: Uri,
        id: String,
    ): String? =
        resolver
            .query(
                DocumentsContract.buildDocumentUriUsingTree(tree, id),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
