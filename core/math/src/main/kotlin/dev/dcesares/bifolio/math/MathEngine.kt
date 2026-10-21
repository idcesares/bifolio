package dev.dcesares.bifolio.math

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.hrm.latex.parser.LatexParser
import com.hrm.latex.parser.ParseDiagnostic
import com.hrm.latex.parser.model.LatexNode
import com.hrm.latex.renderer.export.ExportConfig
import com.hrm.latex.renderer.export.ImageFormat
import com.hrm.latex.renderer.export.LatexExporterState
import com.hrm.latex.renderer.export.rememberLatexExporter
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** What the renderer made of one formula at one size (SYN-12, PAG-07). */
public sealed interface MathMeasurement {
    /** Natural size in px at the density and font scale of the engine. Both are at least 1. */
    public data class Sized(
        val width: Int,
        val height: Int,
    ) : MathMeasurement

    public data class Failed(
        val failure: MathFailure,
    ) : MathMeasurement
}

/** What the reader needs of the math renderer. [MathEngine] is the real one; tests use fakes. */
public interface MathService {
    public val density: Float
    public val fontScale: Float

    /** Measures [tex] (the author's source) at [fontSp]. Failures are answers too and are remembered. */
    public suspend fun measure(
        tex: String,
        fontSp: Float,
        display: Boolean = true,
    ): MathMeasurement

    /**
     * Draws [tex] as an alpha mask in black, [scale] times its natural size (never above 1), so the caller tints it
     * with the theme foreground and a theme change costs nothing. Null when the formula fails or cannot be drawn.
     */
    public suspend fun render(
        tex: String,
        fontSp: Float,
        scale: Float,
        display: Boolean = true,
    ): Bitmap?
}

/** Natural size of a formula in px, as the renderer reports it. */
internal class Dims(
    val widthPx: Float,
    val heightPx: Float,
)

/** The renderer library behind a seam, so the guards of [MathEngine] are tested with faults the library never shows. */
internal interface MathBackend {
    /** No error diagnostic, no unknown command and no error node in what the renderer's parser made of [prepared]. */
    fun parsesCleanly(prepared: String): Boolean

    fun measure(
        prepared: String,
        fontSp: Float,
        display: Boolean,
    ): Dims?

    fun export(
        prepared: String,
        fontSp: Float,
        scale: Float,
        display: Boolean,
    ): Bitmap?
}

internal class LibraryBackend(
    private val measurer: LatexMeasurerState,
    private val exporter: LatexExporterState,
    private val base: LatexConfig,
) : MathBackend {
    private val parser = LatexParser()

    override fun parsesCleanly(prepared: String): Boolean {
        val result = parser.parseWithDiagnostics(prepared)
        if (result.errors.any { it.severity == ParseDiagnostic.Severity.ERROR }) return false
        val pending = ArrayDeque<LatexNode>()
        pending.add(result.document)
        var visited = 0
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (++visited > MAX_NODES) return false
            if (node is LatexNode.Command || node is LatexNode.Error) return false
            pending.addAll(node.children())
        }
        return true
    }

    override fun measure(
        prepared: String,
        fontSp: Float,
        display: Boolean,
    ): Dims? =
        measurer.measure(prepared, base.copy(fontSize = fontSp.sp), display)?.let { Dims(it.widthPx, it.heightPx) }

    override fun export(
        prepared: String,
        fontSp: Float,
        scale: Float,
        display: Boolean,
    ): Bitmap? {
        val config = ExportConfig(scale, ImageFormat.PNG, true, QUALITY)
        val image = exporter.export(prepared, base.copy(fontSize = fontSp.sp), config, display)
        return image?.imageBitmap?.asAndroidBitmap()
    }

    private companion object {
        const val MAX_NODES = 20_000
        const val QUALITY = 100
    }
}

/**
 * The math renderer behind the adapter (ADR-0006). Every guard of the ADR lives here or in [TexSanitizer]:
 * KaTeX fonts injected before the first measurement, validation before the renderer, `Throwable` caught around every
 * call, size bounds on the result, access serialized on one worker, no link and no color from the author.
 *
 * Sizes are in px at [density] and [fontScale], for a font size given in sp, so a formula follows the same text
 * scale as the body (TYP-01). Nothing here touches the main thread: call from a coroutine, results are cached.
 * No type of the renderer appears in this API, so a replacement (RaTeX is the documented fallback) changes only
 * this module.
 */
public class MathEngine internal constructor(
    private val backend: MathBackend,
    override val density: Float,
    override val fontScale: Float,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : MathService {
    // The renderer is not known to be thread safe (ADR-0006 guard 8): one call at a time.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val gate = dispatcher.limitedParallelism(1)
    private val sizes = BoundedMap<Key, MathMeasurement>(MEASURE_CACHE)

    private data class Key(
        val tex: String,
        val fontSp: Float,
        val display: Boolean,
    )

    override suspend fun measure(
        tex: String,
        fontSp: Float,
        display: Boolean,
    ): MathMeasurement =
        onGate(MathMeasurement.Failed(MathFailure.RENDERER)) {
            val key = Key(tex, fontSp, display)
            sizes[key] ?: measureNow(tex, fontSp, display).also { sizes[key] = it }
        }

    override suspend fun render(
        tex: String,
        fontSp: Float,
        scale: Float,
        display: Boolean,
    ): Bitmap? =
        onGate(null) {
            val prepared = (TexSanitizer.prepare(tex) as? PreparedTex.Ready)?.tex
            val key = Key(tex, fontSp, display)
            val known = sizes[key] ?: measureNow(tex, fontSp, display).also { sizes[key] = it }
            if (prepared == null || known !is MathMeasurement.Sized) return@onGate null
            val started = clockMillis()
            val bitmap = backend.export(prepared, fontSp, scale.coerceIn(MIN_SCALE, 1f), display)
            // A drawing over the budget is not trusted again: the formula shows its source from now on.
            if (clockMillis() - started > MathLimits.CALL_BUDGET_MILLIS) {
                sizes[key] = MathMeasurement.Failed(MathFailure.TIMEOUT)
                null
            } else {
                bitmap
            }
        }

    /** The same as [measure] without the cache, for tests that compare a first measurement with a later one. */
    internal suspend fun remeasure(
        tex: String,
        fontSp: Float,
        display: Boolean = true,
    ): MathMeasurement = onGate(MathMeasurement.Failed(MathFailure.RENDERER)) { measureNow(tex, fontSp, display) }

    private fun measureNow(
        tex: String,
        fontSp: Float,
        display: Boolean,
    ): MathMeasurement {
        val prepared =
            when (val result = TexSanitizer.prepare(tex)) {
                is PreparedTex.Rejected -> return MathMeasurement.Failed(result.failure)
                is PreparedTex.Ready -> result.tex
            }
        val started = clockMillis()
        val answer = measurePrepared(prepared, fontSp, display)
        // The library cannot be interrupted, so a call over the budget is only answered late. It is remembered as a
        // failure, which keeps a slow formula from being measured or drawn again.
        if (clockMillis() - started > MathLimits.CALL_BUDGET_MILLIS) return MathMeasurement.Failed(MathFailure.TIMEOUT)
        return answer
    }

    private fun measurePrepared(
        prepared: String,
        fontSp: Float,
        display: Boolean,
    ): MathMeasurement {
        if (!backend.parsesCleanly(prepared)) return MathMeasurement.Failed(MathFailure.PARSE)
        val dims = backend.measure(prepared, fontSp, display)
        val fontPx = fontSp * density * fontScale
        val ok =
            dims != null &&
                dims.widthPx > 0f &&
                dims.heightPx > 0f &&
                dims.widthPx <= fontPx * MathLimits.MAX_WIDTH_EM &&
                dims.heightPx <= fontPx * MathLimits.MAX_HEIGHT_EM
        if (dims == null || !ok) return MathMeasurement.Failed(MathFailure.SIZE)
        return MathMeasurement.Sized(
            ceil(dims.widthPx).toInt().coerceAtLeast(1),
            ceil(dims.heightPx).toInt().coerceAtLeast(1),
        )
    }

    // Throwable on purpose: a hostile formula may end in StackOverflowError or OutOfMemoryError, which must show
    // the source, not close the reader (ADR-0006 guard 4). Cancellation passes through.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> onGate(
        failure: T,
        block: () -> T,
    ): T =
        withContext(gate) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failure
            }
        }

    private companion object {
        const val MEASURE_CACHE = 512
        const val MIN_SCALE = 0.05f
    }
}

/** A tiny LRU, only touched on the engine's single worker. */
private class BoundedMap<K, V>(
    private val capacity: Int,
) {
    private val map =
        object : LinkedHashMap<K, V>(capacity, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
        }

    operator fun get(key: K): V? = map[key]

    operator fun set(
        key: K,
        value: V,
    ) {
        map[key] = value
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * The engine for the density and font scale of the composition it is called in, or null while [enabled] is false: a
 * document without display math loads no font and builds no measurer, so it pays nothing. It is remembered, so it
 * changes only when the density, the font scale or the context change; callers that cached sizes check
 * [MathEngine.density] and [MathEngine.fontScale].
 */
@Composable
public fun rememberMathEngineOrNull(enabled: Boolean): MathEngine? = if (enabled) rememberMathEngine() else null

/** The engine for the composition it is called in; see [rememberMathEngineOrNull]. */
@Composable
public fun rememberMathEngine(): MathEngine {
    val assets = LocalContext.current.assets
    val density = LocalDensity.current
    val fonts = remember(assets) { KatexFonts.load(assets) }
    val config =
        remember(fonts) {
            LatexConfig(
                fontSize = BASE_SP.sp,
                theme = LatexTheme.light(Color.Black, Color.Transparent),
                accessibilityEnabled = false,
                onNodeClick = null,
                onHyperlinkClick = null,
                fontFamilies = fonts,
            )
        }
    val measurer = rememberLatexMeasurer(config)
    val exporter = rememberLatexExporter(config)
    return remember(measurer, exporter, density.density, density.fontScale) {
        MathEngine(LibraryBackend(measurer, exporter, config), density.density, density.fontScale)
    }
}

private const val BASE_SP = 18f
