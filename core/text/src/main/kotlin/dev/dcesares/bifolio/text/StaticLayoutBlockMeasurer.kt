package dev.dcesares.bifolio.text

import dev.dcesares.bifolio.layout.AtomicSize
import dev.dcesares.bifolio.layout.BlockMeasurer
import dev.dcesares.bifolio.layout.LineMetrics
import dev.dcesares.bifolio.layout.TextRole
import dev.dcesares.bifolio.layout.TextStyles
import dev.dcesares.bifolio.model.Block

/**
 * [BlockMeasurer] on top of `StaticLayout` (ADR-0013, OPN-05). Every call builds its own `TextPaint` and
 * layout through [TextLayoutFactory], so instances hold no mutable state and may be called from any
 * background thread. The reader draws with the same factory, so what is counted is what is shown.
 *
 * - TYP-04: high quality breaking for body text, balanced for headings and for columns narrower than
 *   `45 x charW`.
 * - TYP-05: platform hyphenation with the document locale.
 * - TYP-06: justification only when requested, hyphenation is on and the measure is at least `45 x charW`.
 */
public class StaticLayoutBlockMeasurer(
    config: TextStyleConfig,
) : BlockMeasurer {
    private val factory = TextLayoutFactory(config)

    override fun measureLines(
        text: String,
        role: TextRole,
        widthPx: Int,
        styles: TextStyles,
    ): LineMetrics {
        if (text.isEmpty()) return EMPTY
        val layout = factory.layout(text, role, maxOf(1, widthPx), styles)
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
        styles: TextStyles,
    ): Int = factory.intrinsicWidth(text, role, styles)

    /**
     * Placeholder until images and math are measured for real (M5): a 16:9 box at the given width.
     * The paginator scales atomic blocks to fit (PAG-07).
     */
    override fun measureAtomic(
        block: Block,
        widthPx: Int,
    ): AtomicSize {
        val w = maxOf(1, widthPx)
        return AtomicSize(w, maxOf(1, w * RATIO_H / RATIO_W))
    }

    internal companion object {
        private val EMPTY = LineMetrics(0, IntArray(0), IntArray(0), IntArray(0), IntArray(0))
        private const val RATIO_W = 16
        private const val RATIO_H = 9

        fun balanced(
            config: TextStyleConfig,
            role: TextRole,
            widthPx: Int,
        ): Boolean = TextLayoutFactory.balanced(config, role, widthPx)

        fun justifies(
            config: TextStyleConfig,
            widthPx: Int,
        ): Boolean = TextLayoutFactory.justifies(config, widthPx)
    }
}
