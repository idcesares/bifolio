package dev.dcesares.bifolio.text

import android.content.res.AssetManager
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import java.util.concurrent.ConcurrentHashMap

/**
 * The faces the reader draws and measures with (TYP-01). Equal font sets give equal glyph metrics, so a
 * font set is part of the layout identity through [TextStyleConfig].
 */
public interface FontSet {
    /** Body face at [weight] (100 to 900), upright or italic. Also used for headings. */
    public fun body(
        weight: Int,
        italic: Boolean,
    ): Typeface

    public fun code(): Typeface
}

/** Platform serif and monospace. The default, and the fallback when a bundled font cannot be loaded. */
public object SystemFontSet : FontSet {
    override fun body(
        weight: Int,
        italic: Boolean,
    ): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SERIF, weight, italic)
        } else {
            Typeface.create(Typeface.SERIF, styleOf(weight, italic))
        }

    override fun code(): Typeface = Typeface.MONOSPACE

    private fun styleOf(
        weight: Int,
        italic: Boolean,
    ): Int =
        when {
            weight >= BOLD_FROM && italic -> Typeface.BOLD_ITALIC
            weight >= BOLD_FROM -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

    private const val BOLD_FROM = 600
}

/**
 * Variable fonts read from the app assets (API 26 `Typeface.Builder`). One file per slant; the weight is a
 * `wght` variation. A file that fails to load falls back to [SystemFontSet] so text is never blank.
 */
public class AssetFontSet(
    private val assets: AssetManager,
    private val upright: String,
    private val italic: String,
    private val mono: String,
) : FontSet {
    private val cache = ConcurrentHashMap<String, Typeface>()

    override fun body(
        weight: Int,
        italic: Boolean,
    ): Typeface =
        cache.getOrPut("${if (italic) this.italic else upright}@$weight") {
            load(if (italic) this.italic else upright, weight) ?: SystemFontSet.body(weight, italic)
        }

    override fun code(): Typeface =
        cache.getOrPut("$mono@$CODE_WEIGHT") { load(mono, CODE_WEIGHT) ?: SystemFontSet.code() }

    private fun load(
        path: String,
        weight: Int,
    ): Typeface? =
        runCatching {
            Typeface
                .Builder(assets, path)
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .build()
        }.getOrNull()

    override fun equals(other: Any?): Boolean =
        other is AssetFontSet && other.upright == upright && other.italic == italic && other.mono == mono

    override fun hashCode(): Int = (upright.hashCode() * HASH + italic.hashCode()) * HASH + mono.hashCode()

    private companion object {
        const val CODE_WEIGHT = 400
        const val HASH = 31
    }
}

/** Swaps the typeface of a span of text for one face of a [FontSet]; replaces `StyleSpan` for bold and italic. */
internal class FontSetSpan(
    private val face: Typeface,
) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = face
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = face
    }
}
