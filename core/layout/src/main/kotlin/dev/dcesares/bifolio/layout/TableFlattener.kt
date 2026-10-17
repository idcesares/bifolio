package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.TableRow
import dev.dcesares.bifolio.model.plainText

/**
 * Flattens a GFM table into one line per row (PAG-06). The header is row 0 and is repeated on continuation
 * pages by the paginator. Columns keep their natural width when they fit, wrap proportionally while each
 * stays readable, and otherwise stay unwrapped: the table is then a framed figure that scrolls sideways.
 */
internal class TableFlattener(
    private val document: Document,
    private val measurer: BlockMeasurer,
    private val metrics: FlowMetrics,
) {
    fun flatten(
        block: Table,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        val rows = ArrayList<TableRow>(block.rows.size + 1)
        rows.add(block.header)
        rows.addAll(block.rows)
        val texts = Array(rows.size) { r -> Array(block.columnCount) { c -> cellText(rows[r], c) } }
        val widths = columnWidths(texts, block.columnCount, width)
        val lineStarts = sourceLineStarts(block)
        out.beginRun()
        for (r in rows.indices) {
            val h = rowHeight(texts[r], widths)
            val s0 = rowSource(lineStarts, block, r)
            val s1 = rowSourceEnd(lineStarts, block, r)
            out.beginUnit(false)
            out.line(0, h, s0 - top, s1 - top, texts[r].sumOf { it.length })
            out.advance(h)
            if (r == 0) {
                out.headerLines = 1
                out.headerHeight = h
            }
        }
    }

    private fun cellText(
        row: TableRow,
        column: Int,
    ): String =
        row.cells
            .getOrNull(column)
            ?.content
            ?.let { plainText(it) } ?: ""

    /** Natural widths when they fit; proportional wrapping when the columns stay readable; else unwrapped. */
    private fun columnWidths(
        texts: Array<Array<String>>,
        columns: Int,
        width: Int,
    ): IntArray {
        val pad = 2 * metrics.tableCellPadding
        val natural = IntArray(columns)
        for (row in texts) {
            for (c in 0 until columns) {
                natural[c] = maxOf(natural[c], measurer.intrinsicWidth(row[c], TextRole.TABLE_CELL) + pad)
            }
        }
        val total = natural.sum()
        return when {
            total <= width || columns * metrics.tableMinColumn > width -> natural
            else ->
                IntArray(
                    columns,
                ) { c -> maxOf(metrics.tableMinColumn, (width.toLong() * natural[c] / total).toInt()) }
        }
    }

    private fun rowHeight(
        cells: Array<String>,
        widths: IntArray,
    ): Int {
        val pad = 2 * metrics.tableCellPadding
        var h = 0
        for (c in cells.indices) {
            val text = cells[c].ifEmpty { " " }
            val m = measurer.measureLines(text, TextRole.TABLE_CELL, maxOf(widths[c] - pad, 1))
            h = maxOf(h, m.height)
        }
        return h + pad
    }

    private fun sourceLineStarts(block: Table): IntArray {
        val src = document.source
        val starts = ArrayList<Int>()
        var i = block.range.start
        while (i < block.range.end) {
            starts.add(i)
            val nl = src.indexOf('\n', i)
            i = if (nl < 0 || nl >= block.range.end) block.range.end else nl + 1
        }
        return IntArray(starts.size) { starts[it] }
    }

    /** Row 0 owns the header and the delimiter line; row r owns source line r + 1. */
    private fun rowSource(
        starts: IntArray,
        block: Table,
        row: Int,
    ): Int = if (starts.isEmpty()) block.range.start else starts[minOf(if (row == 0) 0 else row + 1, starts.size - 1)]

    private fun rowSourceEnd(
        starts: IntArray,
        block: Table,
        row: Int,
    ): Int {
        val next = if (row == 0) 2 else row + 2
        return if (next < starts.size) starts[next] else block.range.end
    }
}
