package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block

/**
 * Deterministic measurer: every character is [charWidth] px wide, every line [lineHeight] px tall,
 * lines break after spaces (the space stays at the end of its line) and words longer than a line are
 * hard-broken. `\n` ends a line and stays in it, so lines always tile the text.
 */
internal class FakeBlockMeasurer(
    val charWidth: Int = 10,
    val lineHeight: Int = 20,
    private val atomicSize: (Block) -> AtomicSize = { AtomicSize(200, 100) },
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
        var lineStart = 0
        while (lineStart < text.length) {
            starts.add(lineStart)
            lineStart = nextBreak(text, lineStart, perLine)
        }
        val n = starts.size
        val s = IntArray(n) { starts[it] }
        val e = IntArray(n) { if (it + 1 < n) s[it + 1] else text.length }
        val h = lineHeight
        return LineMetrics(n, IntArray(n) { it * h }, IntArray(n) { (it + 1) * h }, s, e)
    }

    override fun intrinsicWidth(
        text: String,
        role: TextRole,
        styles: TextStyles,
    ): Int = (text.lineSequence().maxOfOrNull { it.length } ?: 0) * charWidth

    override fun measureAtomic(
        block: Block,
        widthPx: Int,
    ): AtomicSize = atomicSize(block)

    /** Index just after the line that starts at [from]. */
    private fun nextBreak(
        text: String,
        from: Int,
        perLine: Int,
    ): Int {
        val limit = minOf(text.length, from + perLine)
        var lastSpace = -1
        var i = from
        while (i < limit) {
            val c = text[i]
            if (c == '\n') return i + 1
            if (c == ' ') lastSpace = i
            i++
        }
        return when {
            limit == text.length -> limit
            text[limit] == ' ' -> limit + 1
            lastSpace >= 0 -> lastSpace + 1
            else -> limit
        }
    }
}
