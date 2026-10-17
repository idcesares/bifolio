package dev.dcesares.bifolio.feature.reader

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.dcesares.bifolio.parse.SizePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * The raw result of reading a URI. [bytes] is null when the file is above the refusal limit (DOC-05), in which
 * case nothing was kept and [declaredSize] says how big it is (or the cap that was hit).
 */
public class RawDocument(
    public val displayName: String?,
    public val declaredSize: Long,
    public val bytes: ByteArray?,
)

public interface DocumentReader {
    /** Reads [uri] fully, or reports an oversized file without keeping it. Throws [IOException] when unreadable. */
    public suspend fun read(uri: String): RawDocument
}

private const val BUFFER_SIZE = 64 * 1024

/** Reads through the Storage Access Framework. The app never writes to the URI (read-only). */
public class ContentResolverDocumentReader(
    private val resolver: ContentResolver,
) : DocumentReader {
    override suspend fun read(uri: String): RawDocument =
        withContext(Dispatchers.IO) {
            val parsed = Uri.parse(uri)
            val (name, size) = queryNameAndSize(parsed)
            if (size != null && size > SizePolicy.REFUSE_ABOVE_BYTES) {
                return@withContext RawDocument(name, size, null)
            }
            val limit = SizePolicy.REFUSE_ABOVE_BYTES + 1
            val bytes =
                try {
                    val stream = resolver.openInputStream(parsed) ?: throw IOException("no stream for $uri")
                    stream.use { readCapped(it, limit) }
                } catch (e: SecurityException) {
                    throw IOException("no permission to read $uri", e)
                }
            if (bytes.size.toLong() >= limit) {
                RawDocument(name, limit, null)
            } else {
                RawDocument(name, size ?: bytes.size.toLong(), bytes)
            }
        }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> =
        try {
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            resolver.query(uri, columns, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null to null
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !c.isNull(nameIndex)) c.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !c.isNull(sizeIndex)) c.getLong(sizeIndex) else null
                name to size
            } ?: (null to null)
        } catch (e: SecurityException) {
            throw IOException("no permission to read $uri", e)
        }

    private fun readCapped(
        input: InputStream,
        limit: Long,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (total < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
