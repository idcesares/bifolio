package dev.dcesares.bifolio.data

import java.net.URLDecoder
import java.net.URLEncoder

/** The provider authority and the document id a `content:` URI stands for. */
public data class DocumentRef(
    val authority: String,
    val id: String,
)

/**
 * String-level reading of Storage Access Framework URIs, so containment and relative paths are decided without
 * touching the content resolver (and test on the JVM). Formats: `content://AUTHORITY/document/ID`,
 * `content://AUTHORITY/tree/ID` and `content://AUTHORITY/tree/ID/document/ID`, with every id percent-encoded.
 */
public object FolderPaths {
    private const val SCHEME = "content://"
    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    private fun decode(part: String): String = URLDecoder.decode(part.replace("+", "%2B"), "UTF-8")

    private fun encode(part: String): String = URLEncoder.encode(part, "UTF-8").replace("+", "%20")

    private fun segments(uri: String): Pair<String, List<String>>? {
        if (!uri.startsWith(SCHEME, ignoreCase = true)) return null
        val rest = uri.substring(SCHEME.length).substringBefore('?').substringBefore('#')
        val authority = rest.substringBefore('/')
        if (authority.isEmpty() || '/' !in rest) return null
        return authority to rest.substringAfter('/').split('/')
    }

    /** The document a document URI (or the document part of a tree document URI) names, or null. */
    public fun documentOf(uri: String): DocumentRef? {
        val (authority, parts) = segments(uri) ?: return null
        val at = parts.lastIndexOf("document")
        if (at < 0 || at + 1 >= parts.size) return null
        val id = runCatching { decode(parts[at + 1]) }.getOrNull() ?: return null
        return id.takeIf { it.isNotEmpty() }?.let { DocumentRef(authority, it) }
    }

    /** The root document of a tree URI, or null when [uri] is not a tree URI. */
    public fun treeOf(uri: String): DocumentRef? {
        val (authority, parts) = segments(uri) ?: return null
        val at = parts.indexOf("tree")
        if (at < 0 || at + 1 >= parts.size) return null
        val id = runCatching { decode(parts[at + 1]) }.getOrNull() ?: return null
        return id.takeIf { it.isNotEmpty() }?.let { DocumentRef(authority, it) }
    }

    /**
     * Names from the tree root down to [docId] (file name last), when the ids reveal the path: the id of a
     * descendant is the id of the tree, a `/`, then the path (external storage and most file based providers).
     * Null when [docId] is not below [treeId], including a sibling whose name only starts with the same letters.
     */
    public fun namesBelow(
        treeId: String,
        docId: String,
    ): List<String>? {
        val prefix = if (treeId.endsWith(":") || treeId.endsWith("/")) treeId else "$treeId/"
        if (docId.length <= prefix.length || !docId.startsWith(prefix)) return null
        val names = docId.substring(prefix.length).split('/')
        return names.takeIf { list -> list.none { it.isEmpty() } }
    }

    /**
     * The URI to hand to `EXTRA_INITIAL_URI`: the folder that holds [documentUri], when its id reveals one,
     * otherwise the document itself (the picker then opens near it). Null for a URI that is not a document.
     */
    public fun pickerStart(documentUri: String): String? {
        val ref = documentOf(documentUri) ?: return null
        return folderOf(documentUri) ?: "$SCHEME${ref.authority}/document/${encode(ref.id)}"
    }

    /**
     * The folder that holds [documentUri] as a document URI, when the id reveals it (a `/` in the id, or a file at
     * the root of an external storage volume). It names the folder a refusal of the folder offer applies to
     * (UI-14), so every document in it shares the answer. Null when the id does not say.
     */
    public fun folderOf(documentUri: String): String? {
        val ref = documentOf(documentUri) ?: return null
        val parent =
            when {
                '/' in ref.id -> ref.id.substringBeforeLast('/').takeIf { it.isNotEmpty() }
                ref.authority.equals(EXTERNAL_STORAGE, ignoreCase = true) && ':' in ref.id ->
                    ref.id.substringBefore(':') + ":"
                else -> null
            } ?: return null
        return "$SCHEME${ref.authority}/document/${encode(parent)}"
    }
}
