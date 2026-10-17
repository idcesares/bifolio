package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.AtomicSize
import dev.dcesares.bifolio.layout.BlockMeasurer
import dev.dcesares.bifolio.layout.FlowMetrics
import dev.dcesares.bifolio.layout.LineMetrics
import dev.dcesares.bifolio.layout.TextRole
import dev.dcesares.bifolio.layout.TextStyles
import dev.dcesares.bifolio.layout.WindowSpec
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.text.TextStyleConfig

/**
 * Deterministic measurer for JVM tests: every character is [charWidth] px wide, every line [lineHeight] px tall,
 * lines break after spaces and long words are hard-broken.
 */
internal class GridMeasurer(
    private val charWidth: Int = 10,
    private val lineHeight: Int = 20,
) : BlockMeasurer {
    override fun measureLines(
        text: String,
        role: TextRole,
        widthPx: Int,
        styles: TextStyles,
    ): LineMetrics {
        if (text.isEmpty()) return LineMetrics(0, IntArray(0), IntArray(0), IntArray(0), IntArray(0))
        val perLine = maxOf(1, widthPx / charWidth)
        val starts = ArrayList<Int>()
        var from = 0
        while (from < text.length) {
            starts.add(from)
            from = nextBreak(text, from, perLine)
        }
        val n = starts.size
        val s = IntArray(n) { starts[it] }
        val e = IntArray(n) { if (it + 1 < n) s[it + 1] else text.length }
        return LineMetrics(n, IntArray(n) { it * lineHeight }, IntArray(n) { (it + 1) * lineHeight }, s, e)
    }

    override fun intrinsicWidth(
        text: String,
        role: TextRole,
        styles: TextStyles,
    ): Int = (text.lineSequence().maxOfOrNull { it.length } ?: 0) * charWidth

    override fun measureAtomic(
        block: Block,
        widthPx: Int,
    ): AtomicSize = AtomicSize(widthPx, widthPx / 2)

    private fun nextBreak(
        text: String,
        from: Int,
        perLine: Int,
    ): Int {
        val limit = minOf(text.length, from + perLine)
        var lastSpace = -1
        for (i in from until limit) {
            if (text[i] == '\n') return i + 1
            if (text[i] == ' ') lastSpace = i
        }
        return when {
            limit == text.length -> limit
            text[limit] == ' ' -> limit + 1
            lastSpace >= 0 -> lastSpace + 1
            else -> limit
        }
    }
}

/** A [TextEngine] without Android: the window math is real, the measuring is the [GridMeasurer]. */
internal class GridTextEngine : TextEngine {
    var prepared = 0
        private set

    override fun prepare(request: PageRequest): PreparedText {
        prepared++
        val config = TextStyleConfig(bodySizePx = GRID_BODY_PX, charWidthPx = GRID_CHAR_PX)
        val spec =
            WindowSpec(
                widthPx = request.widthPx,
                heightPx = request.heightPx,
                density = request.density,
                charWidthPx = GRID_CHAR_PX,
                metrics = FlowMetrics(blockGap = 12, innerGap = 6),
            )
        return PreparedText(GridMeasurer(), spec, config)
    }

    private companion object {
        const val GRID_BODY_PX = 20f
        const val GRID_CHAR_PX = 10f
    }
}
