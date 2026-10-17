package dev.dcesares.bifolio.text

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import dev.dcesares.bifolio.layout.AtomicSize
import dev.dcesares.bifolio.layout.BlockMeasurer
import dev.dcesares.bifolio.layout.LineMetrics
import dev.dcesares.bifolio.layout.TextRole
import dev.dcesares.bifolio.model.Block

/**
 * [BlockMeasurer] on top of `StaticLayout` (ADR-0013, OPN-05). Every call builds its own `TextPaint` and
 * layout, so instances hold no mutable state and may be called from any background thread.
 *
 * - TYP-04: high quality breaking for body text, balanced for headings and for columns narrower than
 *   `45 x charW`.
 * - TYP-05: platform hyphenation with the document locale.
 * - TYP-06: justification only when requested, hyphenation is on and the measure is at least `45 x charW`.
 */
public class StaticLayoutBlockMeasurer(
    private val config: TextStyleConfig,
) : BlockMeasurer {
    override fun measureLines(
        text: String,
        role: TextRole,
        widthPx: Int,
    ): LineMetrics {
        if (text.isEmpty()) return EMPTY
        val width = maxOf(1, widthPx)
        val layout = layoutOf(text, role, width)
        val total = layout.lineCount
        val tops = IntArray(total)
        val bottoms = IntArray(total)
        val starts = IntArray(total)
        val ends = IntArray(total)
        var n = 0
        for (i in 0 until total) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            // Empty lines (after a final hard break, or a width too small for one glyph) have no
            // characters; dropping them keeps the lines tiling the text.
            if (end <= start) continue
            tops[n] = layout.getLineTop(i)
            bottoms[n] = layout.getLineBottom(i)
            starts[n] = start
            ends[n] = end
            n++
        }
        return LineMetrics(n, tops, bottoms, starts, ends)
    }

    override fun intrinsicWidth(
        text: String,
        role: TextRole,
    ): Int {
        if (text.isEmpty()) return 0
        val paint = paintOf(role)
        var widest = 0f
        var from = 0
        while (from <= text.length) {
            var to = text.indexOf('\n', from)
            if (to < 0) to = text.length
            if (to > from) widest = maxOf(widest, paint.measureText(text, from, to))
            from = to + 1
        }
        return Math.ceil(widest.toDouble()).toInt()
    }

    /**
     * Placeholder until images and math are measured for real (M4 and later): a 16:9 box at the given width.
     * The paginator scales atomic blocks to fit (PAG-07).
     */
    override fun measureAtomic(
        block: Block,
        widthPx: Int,
    ): AtomicSize {
        val w = maxOf(1, widthPx)
        return AtomicSize(w, maxOf(1, w * RATIO_H / RATIO_W))
    }

    private fun layoutOf(
        text: String,
        role: TextRole,
        width: Int,
    ): StaticLayout {
        val balanced = balanced(config, role, width)
        val body = role == TextRole.BODY
        val builder =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paintOf(role), width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, config.lineSpacingMultiplier)
                .setIncludePad(false)
                .setBreakStrategy(strategyOf(role, balanced))
                .setHyphenationFrequency(
                    if (body && config.hyphenate) {
                        Layout.HYPHENATION_FREQUENCY_NORMAL
                    } else {
                        Layout.HYPHENATION_FREQUENCY_NONE
                    },
                ).setJustificationMode(
                    if (body && justifies(config, width)) {
                        Layout.JUSTIFICATION_MODE_INTER_WORD
                    } else {
                        Layout.JUSTIFICATION_MODE_NONE
                    },
                )
        return builder.build()
    }

    private fun strategyOf(
        role: TextRole,
        balanced: Boolean,
    ): Int =
        when {
            role == TextRole.CODE -> Layout.BREAK_STRATEGY_SIMPLE
            balanced -> Layout.BREAK_STRATEGY_BALANCED
            else -> Layout.BREAK_STRATEGY_HIGH_QUALITY
        }

    private fun paintOf(role: TextRole): TextPaint {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        paint.textSize = config.bodySizePx * scaleOf(role)
        paint.textLocale = config.locale
        if (role == TextRole.CODE) paint.typeface = Typeface.MONOSPACE
        if (isHeading(role)) paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        return paint
    }

    internal companion object {
        private val EMPTY = LineMetrics(0, IntArray(0), IntArray(0), IntArray(0), IntArray(0))
        private const val MEASURE_CHARS = 45
        private const val RATIO_W = 16
        private const val RATIO_H = 9
        private const val H1_SCALE = 2.0f
        private const val H2_SCALE = 1.6f
        private const val H3_SCALE = 1.35f
        private const val H4_SCALE = 1.2f
        private const val H5_SCALE = 1.1f
        private const val CODE_SCALE = 0.9f
        private const val CELL_SCALE = 0.95f

        fun isHeading(role: TextRole): Boolean = role.ordinal in TextRole.HEADING_1.ordinal..TextRole.HEADING_6.ordinal

        fun scaleOf(role: TextRole): Float =
            when (role) {
                TextRole.HEADING_1 -> H1_SCALE
                TextRole.HEADING_2 -> H2_SCALE
                TextRole.HEADING_3 -> H3_SCALE
                TextRole.HEADING_4 -> H4_SCALE
                TextRole.HEADING_5 -> H5_SCALE
                TextRole.HEADING_6 -> 1.0f
                TextRole.CODE -> CODE_SCALE
                TextRole.TABLE_CELL -> CELL_SCALE
                TextRole.BODY -> 1.0f
            }

        /** TYP-04: balanced for headings and for measures under `45 x charW`. */
        fun balanced(
            config: TextStyleConfig,
            role: TextRole,
            widthPx: Int,
        ): Boolean = isHeading(role) || (role != TextRole.CODE && widthPx < MEASURE_CHARS * config.charWidthPx)

        /** TYP-06: justify only with hyphenation on and a measure of at least `45 x charW`. */
        fun justifies(
            config: TextStyleConfig,
            widthPx: Int,
        ): Boolean = config.justify && config.hyphenate && widthPx >= MEASURE_CHARS * config.charWidthPx
    }
}
