package dev.dcesares.bifolio.feature.reader

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.dcesares.bifolio.data.FolderGrant
import dev.dcesares.bifolio.data.OpenedFile
import dev.dcesares.bifolio.layout.ImageSize
import dev.dcesares.bifolio.layout.ImageSizeProvider
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.parse.ImageSource
import dev.dcesares.bifolio.parse.ImageSources
import dev.dcesares.bifolio.parse.RejectReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Every image block of [blocks], also inside lists, quotes, callouts, details and footnote definitions. */
internal fun imageBlocksOf(blocks: List<Block>): List<ImageBlock> {
    val found = ArrayList<ImageBlock>()

    fun walk(list: List<Block>) {
        for (block in list) {
            when (block) {
                is ImageBlock -> found.add(block)
                is ListBlock -> block.items.forEach { walk(it.blocks) }
                is BlockQuote -> walk(block.blocks)
                is Callout -> walk(block.blocks)
                is HtmlDetails -> walk(block.blocks)
                is FootnoteDefinition -> walk(block.blocks)
                else -> Unit
            }
        }
    }
    walk(blocks)
    return found
}

/**
 * The sizes the images of one document are known to have, frozen (PAG-07, SYN-08). It is the [ImageSizeProvider] of
 * a pagination: the answer for a block never changes, so a run that holds a snapshot cannot see a size arrive in the
 * middle. [key] is the `imageKey` of the layout cache; it is unique to this object, so a layout made from an older
 * snapshot is never taken for this one. A size that was reported is never taken back.
 */
internal class ImageSizeSnapshot private constructor(
    val key: Int,
    private val sizes: Map<Int, ImageSize.Known>,
) : ImageSizeProvider {
    /** Sizes are in image pixels; [scaledBy] turns them into layout px. */
    override fun sizeOf(block: ImageBlock): ImageSize = sizes[block.range.start] ?: ImageSize.Unknown

    val count: Int get() = sizes.size

    /** This snapshot plus [found] (keyed by block start); the same object when nothing new arrived. */
    fun plus(found: Map<Int, ImageSize.Known>): ImageSizeSnapshot {
        val fresh = found.filterKeys { it !in sizes }
        if (fresh.isEmpty()) return this
        return ImageSizeSnapshot(counter.incrementAndGet(), sizes + fresh)
    }

    /** One image pixel is one dp, so a picture keeps its size across densities and shrinks only to fit (PAG-07). */
    fun scaledBy(density: Float): ImageSizeProvider =
        ImageSizeProvider { block ->
            when (val size = sizeOf(block)) {
                is ImageSize.Known ->
                    ImageSize.Known(
                        (size.width * density).roundToInt().coerceAtLeast(1),
                        (size.height * density).roundToInt().coerceAtLeast(1),
                    )
                ImageSize.Unknown -> ImageSize.Unknown
            }
        }

    companion object {
        private val counter = AtomicInteger(0)

        /** No size known; key 0, the same as a layout made without images. */
        val EMPTY = ImageSizeSnapshot(0, emptyMap())
    }
}

/** The offer to grant the folder of a document whose images are relative paths (UI-14). */
public sealed interface ImageBanner {
    public data object Hidden : ImageBanner

    /** [pickerStart] is where the chooser opens (the folder of the document), when it can be derived. */
    public data class Offer(
        val pickerStart: String?,
    ) : ImageBanner

    /** The chosen folder did not contain the document; ask again. */
    public data class WrongFolder(
        val pickerStart: String?,
    ) : ImageBanner
}

private const val PUBLISH_INTERVAL_MILLIS = 400L
private const val DECODE_PARALLELISM = 2
private const val DEFAULT_CACHE_BYTES = 32 * 1024 * 1024

/**
 * The images of one open document: where each comes from, their sizes, and the decoded pixels (SYN-08, PAG-07).
 * [folder] is the granted folder relative sources resolve through, or null when there is none (they stay
 * placeholders). Sizes come from a bounds-only read done in the background and published in batches as
 * [ImageSizeSnapshot]s, so a document with many images repaginates a few times, not once per image.
 *
 * Pixels are decoded on demand at the size of the box they are drawn in, and kept in a cache bounded in bytes.
 * Bitmaps are never recycled by hand: a spread that is still on screen may be drawing one the cache just dropped.
 * A decode that fails changes nothing the layout used: the box keeps its size and shows the alt text.
 */
internal class ImageSession(
    document: Document,
    val folder: FolderGrant?,
    private val openRelative: (List<String>) -> OpenedFile?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    cacheBytes: Int = DEFAULT_CACHE_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val blocks: List<ImageBlock> = imageBlocksOf(document.blocks)
    private val base: List<String> = folder?.base.orEmpty()
    private val sources = ConcurrentHashMap<Int, ImageSource>()
    private val mutableSizes = MutableStateFlow(ImageSizeSnapshot.EMPTY)

    /** Grows as sizes are read; the current value is the provider for the next pagination. */
    val sizes: StateFlow<ImageSizeSnapshot> = mutableSizes

    /** Reads (and, for the box, the image itself) the pixels of the blocks drawn; see [bitmapFor]. */
    val renderer = ImageRenderer(this, scope, io, cacheBytes)

    /** Where [block] points. Decided from its text alone, with the folder's base for relative paths. */
    fun sourceOf(block: ImageBlock): ImageSource =
        sources.getOrPut(block.range.start) { ImageSources.classify(block.source, base) }

    /**
     * True when some image is a relative reference that cannot load without a folder: a relative path, or one that
     * climbs out of the folder of the document (a parent folder could hold it).
     */
    fun needsFolder(): Boolean =
        folder == null &&
            blocks.any { block ->
                when (val source = sourceOf(block)) {
                    is ImageSource.Relative -> true
                    is ImageSource.Rejected ->
                        source.reason == RejectReason.TRAVERSAL && block.source.trim().startsWith("..")
                    else -> false
                }
            }

    /** How to open the bytes of [block], or null when it never loads (remote, rejected, no folder). */
    fun inputOf(block: ImageBlock): (() -> OpenedFile?)? =
        when (val source = sourceOf(block)) {
            is ImageSource.DataUri ->
                ({ OpenedFile(ByteArrayInputStream(source.decode()), source.mimeType) })
            is ImageSource.Relative -> if (folder == null) null else ({ openRelative(source.segments) })
            else -> null
        }

    /** Starts reading the sizes. Cancelled with [scope]. */
    fun start() {
        scope.launch {
            val pending = HashMap<Int, ImageSize.Known>()
            var lastPublish = clock()
            val byDestination = blocks.groupBy { it.source }
            for (group in byDestination.values) {
                val first = group.first()
                val open = inputOf(first) ?: continue
                val probe = withContext(io) { RasterImages.probe(safe(open)) }
                if (probe != null) {
                    val size = ImageSize.Known(probe.width, probe.height)
                    group.forEach { pending[it.range.start] = size }
                }
                if (pending.isNotEmpty() && clock() - lastPublish >= PUBLISH_INTERVAL_MILLIS) {
                    publish(pending)
                    lastPublish = clock()
                }
            }
            publish(pending)
        }
    }

    private fun publish(found: MutableMap<Int, ImageSize.Known>) {
        if (found.isEmpty()) return
        mutableSizes.value = mutableSizes.value.plus(found)
        found.clear()
    }

    /** A provider call may throw (revoked grant, vanished file): that is a missing image, not a crash. */
    private fun safe(open: () -> OpenedFile?): () -> OpenedFile? =
        {
            try {
                open()
            } catch (_: RuntimeException) {
                null
            } catch (_: java.io.IOException) {
                null
            }
        }

    internal fun guarded(open: () -> OpenedFile?): () -> OpenedFile? = safe(open)
}

/**
 * Decoded pixels for the boxes the reader draws (PAG-07). [bitmapFor] never blocks: it returns what is cached and
 * otherwise starts a decode, then bumps [revision] when the pixels land so a surface that read [revision] while
 * drawing draws again.
 */
internal class ImageRenderer(
    private val session: ImageSession,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val cacheBytes: Int,
) {
    private data class Key(
        val block: Int,
        val width: Int,
        val height: Int,
    )

    private val cache =
        object : LruCache<Key, Bitmap>(cacheBytes) {
            override fun sizeOf(
                key: Key,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val pending = ConcurrentHashMap.newKeySet<Key>()
    private val failed = ConcurrentHashMap.newKeySet<Key>()
    private val revisions = AtomicInteger(0)
    private val permits = Semaphore(DECODE_PARALLELISM)

    /** Read by the drawing code; changes whenever a bitmap lands or a decode fails. */
    var revision by mutableIntStateOf(0)
        private set

    /** Subscribes the caller (a composition or a draw) to landed and failed decodes. */
    fun observe(): Int = revision

    val sizes: StateFlow<ImageSizeSnapshot> get() = session.sizes

    /**
     * Whether the [width] x [height] px box of [block] has no pixels to show and never will: remote, rejected, no
     * folder, or a decode that failed at that box size (another size is tried again).
     */
    fun isPlaceholder(
        block: ImageBlock,
        width: Int,
        height: Int,
    ): Boolean = session.inputOf(block) == null || Key(block.range.start, width, height) in failed

    /** Whether the box of a placeholder carries the alt text: yes unless the pixels are only still loading. */
    fun showsAlt(
        block: ImageBlock,
        width: Int,
        height: Int,
    ): Boolean = isPlaceholder(block, width, height) || session.sizes.value.sizeOf(block) is ImageSize.Unknown

    /** The pixels for the [width] x [height] px box of [block], or null while they load or if they never will. */
    fun bitmapFor(
        block: ImageBlock,
        width: Int,
        height: Int,
    ): Bitmap? {
        val key = Key(block.range.start, width, height)
        cache.get(key)?.let { return it }
        request(block, key)
        return null
    }

    /** Starts the decode early (when the column is prepared) so the first draw may already have the pixels. */
    fun warm(
        block: ImageBlock,
        width: Int,
        height: Int,
    ) {
        val key = Key(block.range.start, width, height)
        if (cache.get(key) == null) request(block, key)
    }

    private fun request(
        block: ImageBlock,
        key: Key,
    ) {
        if (key in failed) return
        val open = session.inputOf(block) ?: return
        if (!pending.add(key)) return
        scope.launch {
            try {
                val bitmap =
                    permits.withPermit {
                        withContext(io) { RasterImages.decode(session.guarded(open), key.width, key.height) }
                    }
                // A bitmap the cache could not keep would be decoded again at every draw: treat it as failed.
                if (bitmap != null &&
                    bitmap.allocationByteCount <= cacheBytes
                ) {
                    cache.put(key, bitmap)
                } else {
                    failed.add(key)
                }
            } finally {
                pending.remove(key)
                // Several decodes finish on background threads: count atomically, then publish the value.
                revision = revisions.incrementAndGet()
            }
        }
    }
}
