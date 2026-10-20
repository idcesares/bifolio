package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document

/** Cache key of PAG-10: the content hash of the document and the full [Geometry] value (no hash collisions). */
public data class LayoutKey(
    val contentHash: String,
    val geometry: Geometry,
    /** Changes whenever the image sizes the pagination used change, so a flow made without them is not reused. */
    val imageKey: Int = 0,
)

/**
 * Small thread-safe LRU of complete paginations. Returning to a previous geometry (fold then unfold, rotate
 * back) reuses the earlier result instead of paginating again (PAG-10).
 */
public class LayoutCache(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val entries =
        object : LinkedHashMap<LayoutKey, PageFlow>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutKey, PageFlow>): Boolean =
                size > capacity
        }

    public operator fun get(key: LayoutKey): PageFlow? = synchronized(entries) { entries[key] }

    public fun put(
        key: LayoutKey,
        flow: PageFlow,
    ) {
        require(flow.complete) { "only complete paginations are cached" }
        synchronized(entries) { entries[key] = flow }
    }

    public val size: Int get() = synchronized(entries) { entries.size }

    private companion object {
        const val DEFAULT_CAPACITY = 6
        const val LOAD_FACTOR = 0.75f
    }
}

/** Paginates through a [LayoutCache]: a hit returns at once, a miss paginates around the anchor and stores. */
public class PaginationService(
    private val cache: LayoutCache = LayoutCache(),
) {
    public fun paginate(
        contentHash: String,
        document: Document,
        measurer: BlockMeasurer,
        geometry: Geometry,
        anchor: Anchor,
        cancel: CancellationSignal = CancellationSignal.NEVER,
        images: ImageSizeProvider = ImageSizeProvider.UNKNOWN,
        imageKey: Int = 0,
        onProgress: (PageFlow) -> Unit = {},
    ): PageFlow? {
        val key = LayoutKey(contentHash, geometry, imageKey)
        cache[key]?.let {
            onProgress(it)
            return it
        }
        val paginator = Paginator(document, measurer, geometry, images)
        val flow = paginator.paginateAround(anchor, cancel, onProgress) ?: return null
        cache.put(key, flow)
        return flow
    }
}
