package dev.dcesares.bifolio.data

import dev.dcesares.bifolio.model.Anchor
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

/** One entry of the library (UI-01). */
public data class RecentDocument(
    val uri: String,
    val title: String,
    val fileName: String,
    val lastOpenedMillis: Long,
    /** 0..1, derived from the anchor and never from a page number (ADR-0005). */
    val progress: Float,
    /** [ContentHash] of the bytes, kept to detect external edits (ANC-05); anchors are keyed by uri. */
    val contentHash: String,
    /** One line from the first paragraph, at most 140 characters, stored when the document was opened (UI-15). */
    val preview: String = "",
    /** Opening failed because the file moved, was deleted or lost its grant (UI-16). */
    val unavailable: Boolean = false,
)

/** SHA-256 of a document's bytes, as lowercase hex. Keys the anchor so it survives a moved or renamed file. */
public object ContentHash {
    public fun of(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/**
 * Text format for the preference values. Fields are percent-encoded so a title can contain any character;
 * records are lines, fields are tab separated. Malformed records are skipped, never fatal.
 */
internal object RecentsCodec {
    private const val LEGACY_RECENT_FIELDS = 6
    private const val RECENT_FIELDS = 8
    private const val ANCHOR_FIELDS = 3

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun dec(value: String): String = URLDecoder.decode(value, "UTF-8")

    fun encodeRecents(list: List<RecentDocument>): String =
        list.joinToString("\n") {
            listOf(
                enc(it.uri),
                enc(it.title),
                enc(it.fileName),
                it.lastOpenedMillis,
                it.progress,
                it.contentHash,
                enc(it.preview),
                if (it.unavailable) 1 else 0,
            ).joinToString("\t")
        }

    @Suppress("MagicNumber") // Field positions of the record format defined by encodeRecents.
    fun decodeRecents(text: String): List<RecentDocument> =
        text
            .lineSequence()
            .mapNotNull { line ->
                val f = line.split('\t')
                val knownSize = f.size == RECENT_FIELDS || f.size == LEGACY_RECENT_FIELDS
                if (!knownSize || f[0].isEmpty()) return@mapNotNull null
                val millis = f[3].toLongOrNull() ?: return@mapNotNull null
                val progress = f[4].toFloatOrNull() ?: return@mapNotNull null
                RecentDocument(
                    uri = dec(f[0]),
                    title = dec(f[1]),
                    fileName = dec(f[2]),
                    lastOpenedMillis = millis,
                    progress = progress.coerceIn(0f, 1f),
                    contentHash = f[5],
                    preview = f.getOrNull(6)?.let(::dec).orEmpty(),
                    unavailable = f.getOrNull(7) == "1",
                )
            }.toList()

    fun encodeAnchors(entries: List<Pair<String, Anchor>>): String =
        entries.joinToString("\n") { (hash, a) -> "$hash\t${a.blockIndex}\t${a.charOffset}" }

    fun decodeAnchors(text: String): List<Pair<String, Anchor>> =
        text
            .lineSequence()
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size != ANCHOR_FIELDS || f[0].isEmpty()) return@mapNotNull null
                val block = f[1].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                val offset = f[2].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
                dec(f[0]) to Anchor(block, offset)
            }.toList()
}
