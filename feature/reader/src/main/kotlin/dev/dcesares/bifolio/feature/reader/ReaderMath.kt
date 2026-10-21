package dev.dcesares.bifolio.feature.reader

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.dcesares.bifolio.layout.MathSize
import dev.dcesares.bifolio.layout.MathSizeProvider
import dev.dcesares.bifolio.math.MathMeasurement
import dev.dcesares.bifolio.math.MathService
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.MathBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Every display formula of [blocks], also inside lists, quotes, callouts, details and footnote definitions. */
internal fun mathBlocksOf(blocks: List<Block>): List<MathBlock> {
    val found = ArrayList<MathBlock>()

    fun walk(list: List<Block>) {
        for (block in list) {
            when (block) {
                is MathBlock -> found.add(block)
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

/** The text settings a formula size was measured under; a size is only valid for a request that matches it. */
internal data class MathTag(
    val density: Float,
    val fontScale: Float,
    val fontSp: Float,
) {
    fun matches(request: PageRequest): Boolean =
        density == request.density && fontScale == request.fontScale && fontSp == request.bodySp
}

/**
 * The formula sizes of one document at one [tag], frozen (PAG-07, SYN-12). Parallel to [ImageSizeSnapshot]: the
 * answer for a block never changes, so a pagination that holds a snapshot cannot see a size arrive in its middle,
 * and [key] (the `mathKey` of the layout cache) is unique to this object. A formula that failed or is still being
 * measured is [MathSize.Unknown]: it takes the same placeholder box, so a late failure never moves the pages.
 */
internal class MathSizeSnapshot private constructor(
    val key: Int,
    val tag: MathTag?,
    private val sizes: Map<Int, MathSize.Known>,
) : MathSizeProvider {
    override fun sizeOf(block: MathBlock): MathSize = sizes[block.range.start] ?: MathSize.Unknown

    /** This snapshot plus [found] (keyed by block start); the same object when nothing new arrived. */
    fun plus(found: Map<Int, MathSize.Known>): MathSizeSnapshot {
        val fresh = found.filterKeys { it !in sizes }
        if (fresh.isEmpty()) return this
        return MathSizeSnapshot(counter.incrementAndGet(), tag, sizes + fresh)
    }

    /** A new, empty snapshot for sizes measured under [tag]. */
    fun restarted(tag: MathTag): MathSizeSnapshot = MathSizeSnapshot(counter.incrementAndGet(), tag, emptyMap())

    /** The provider and key for [request]: these sizes if some exist and were measured for it, otherwise none. */
    fun forRequest(request: PageRequest): Pair<MathSizeProvider, Int> =
        if (tag != null && sizes.isNotEmpty() && tag.matches(request)) this to key else MathSizeProvider.UNKNOWN to 0

    companion object {
        private val counter = AtomicInteger(0)

        /** No size known; key 0, the same as a layout made without math. */
        val EMPTY = MathSizeSnapshot(0, null, emptyMap())
    }
}

private const val PUBLISH_INTERVAL_MILLIS = 300L
private const val CACHE_BYTES = 16 * 1024 * 1024
private const val HASH_PRIME = 31

/**
 * The display formulas of one open document: measured off the main thread by the [MathService] and published in
 * batches as [MathSizeSnapshot]s, so a document with many formulas repaginates a few times, not once per formula
 * (SYN-12, PAG-07). A new engine (density or text scale changed) or font size starts the measuring again.
 */
internal class MathSession(
    document: Document,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val blocks: List<MathBlock> = mathBlocksOf(document.blocks)
    private val mutableSizes = MutableStateFlow(MathSizeSnapshot.EMPTY)

    /** Grows as sizes are measured; the current value is the provider for the next pagination. */
    val sizes: StateFlow<MathSizeSnapshot> = mutableSizes

    internal val failed = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    internal var engine: MathService? = null
        private set

    @Volatile
    internal var fontSp: Float = 0f
        private set
    private var measuring: Job? = null

    /** True once every formula has been measured (or there is none); false until an engine is bound. */
    internal val settled: Boolean get() = engine != null && (blocks.isEmpty() || measuring?.isCompleted == true)

    val renderer = MathRenderer(this, scope)

    /** Starts (or restarts) the measuring for [engine] at [fontSp]; the same pair again changes nothing. */
    fun bind(
        engine: MathService,
        fontSp: Float,
    ) {
        if (engine === this.engine && fontSp == this.fontSp) return
        measuring?.cancel()
        this.engine = engine
        this.fontSp = fontSp
        failed.clear()
        renderer.reset()
        val tag = MathTag(engine.density, engine.fontScale, fontSp)
        mutableSizes.value = mutableSizes.value.restarted(tag)
        if (blocks.isEmpty()) return
        measuring = scope.launch { measureAll(engine, fontSp, mutableSizes.value) }
    }

    private suspend fun measureAll(
        engine: MathService,
        fontSp: Float,
        start: MathSizeSnapshot,
    ) {
        val pending = HashMap<Int, MathSize.Known>()
        var lastPublish = clock()
        var snapshot = start
        for (group in blocks.groupBy { it.tex }.values) {
            when (val m = engine.measure(group.first().tex, fontSp)) {
                is MathMeasurement.Sized -> {
                    val size = MathSize.Known(m.width, m.height)
                    group.forEach { pending[it.range.start] = size }
                }
                is MathMeasurement.Failed -> {
                    group.forEach { failed.add(it.range.start) }
                    renderer.touch()
                }
            }
            if (pending.isNotEmpty() && clock() - lastPublish >= PUBLISH_INTERVAL_MILLIS) {
                snapshot = snapshot.plus(pending)
                pending.clear()
                mutableSizes.value = snapshot
                lastPublish = clock()
            }
        }
        if (pending.isNotEmpty()) mutableSizes.value = snapshot.plus(pending)
    }
}

/**
 * Pixels for the formula boxes the reader draws (PAG-07). The engine returns a black alpha mask already at the size
 * of the box, so the surface tints it with the theme foreground and a theme change costs nothing. [bitmapFor] never
 * blocks: it answers from the cache and otherwise starts the drawing, then bumps [revision] when it lands.
 */
internal class MathRenderer(
    private val session: MathSession,
    private val scope: CoroutineScope,
) {
    private class Key(
        var block: Int,
        var width: Int,
        var height: Int,
    ) {
        fun copy(): Key = Key(block, width, height)

        override fun equals(other: Any?): Boolean =
            other is Key && block == other.block && width == other.width && height == other.height

        override fun hashCode(): Int = (block * HASH_PRIME + width) * HASH_PRIME + height
    }

    // The same object looks up every draw (the main thread draws; the cache never keeps a key it is asked about on a
    // miss or a hit), so a frame allocates nothing here.
    private val probe = Key(0, 0, 0)

    private val cache =
        object : LruCache<Key, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(
                key: Key,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    private val pending = ConcurrentHashMap.newKeySet<Key>()
    private val revisions = AtomicInteger(0)
    private val asked = AtomicInteger(0)

    /** How many drawings were ever started: tests wait until it stops moving and nothing is in flight. */
    internal val requested: Int get() = asked.get()

    /** Read by the drawing code; changes whenever a bitmap lands or a formula is found to fail. */
    var revision by mutableIntStateOf(0)
        private set

    /** Subscribes the caller (a composition or a draw) to landed bitmaps and failures. */
    fun observe(): Int = revision

    val sizes: StateFlow<MathSizeSnapshot> get() = session.sizes

    /** True when no drawing is in flight. */
    internal fun idle(): Boolean = pending.isEmpty()

    internal fun touch() {
        revision = revisions.incrementAndGet()
    }

    internal fun reset() {
        cache.evictAll()
        pending.clear()
        touch()
    }

    /** Whether the formula is known to fail: its source is shown in the placeholder box, with the error mark. */
    fun hasFailed(block: MathBlock): Boolean = block.range.start in session.failed

    /** The mask for the [width] x [height] px box of [block], or null while it is measured or drawn, or for good. */
    fun bitmapFor(
        block: MathBlock,
        width: Int,
        height: Int,
    ): Bitmap? {
        probe.block = block.range.start
        probe.width = width
        probe.height = height
        cache.get(probe)?.let { return it }
        request(block, width, height)
        return null
    }

    /** Starts the drawing early (when the column is prepared) so the first draw may already have it. */
    fun warm(
        block: MathBlock,
        width: Int,
        height: Int,
    ) {
        if (cache.get(Key(block.range.start, width, height)) == null) request(block, width, height)
    }

    private fun request(
        block: MathBlock,
        width: Int,
        height: Int,
    ) {
        // A formula already known to fail is not asked for again: every landing bumps the revision and redraws.
        if (hasFailed(block)) return
        val engine = session.engine ?: return
        val fontSp = session.fontSp
        val natural = session.sizes.value.sizeOf(block) as? MathSize.Known ?: return
        val key = Key(block.range.start, width, height)
        if (!pending.add(key)) return
        asked.incrementAndGet()
        val scale = (key.width.toFloat() / natural.width).coerceAtMost(1f)
        scope.launch {
            try {
                val bitmap = engine.render(block.tex, fontSp, scale)
                // Still the engine this was asked of, and the same box: otherwise the answer is for another layout.
                if (bitmap != null && engine === session.engine && fontSp == session.fontSp) {
                    // A box larger than half the cache (a very large window) raises the cap: no bitmap is dropped
                    // as it lands, so a frame never redraws the same formula again and again.
                    val needed = bitmap.allocationByteCount * 2
                    if (needed > cache.maxSize()) cache.resize(needed)
                    cache.put(key, bitmap)
                } else if (bitmap == null) {
                    session.failed.add(key.block)
                }
            } finally {
                pending.remove(key)
                touch()
            }
        }
    }
}

/** Scales a natural size to a box the way the layout does, for the scroll surface: fit width, 85% of height. */
internal fun fittedMathBox(
    natural: MathSize.Known,
    maxWidthPx: Float,
    maxHeightPx: Float,
): Pair<Int, Int> {
    val scale = minOf(1f, maxWidthPx / natural.width, maxHeightPx / natural.height)
    return (natural.width * scale).roundToInt().coerceAtLeast(1) to
        (natural.height * scale).roundToInt().coerceAtLeast(1)
}
