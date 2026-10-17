package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.ColumnAlignment
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.TableRow

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
        x: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        val rows = ArrayList<TableRow>(block.rows.size + 1)
        rows.add(block.header)
        rows.addAll(block.rows)
        val columns = block.columnCount
        val cells = Array(rows.size) { r -> Array(columns) { c -> cell(rows[r], c) } }
        val widths = columnWidths(cells, columns, width)
        val total = widths.sum()
        val pad = 2 * metrics.tableCellPadding
        val lineStarts = sourceLineStarts(block)
        out.beginRun()
        for (r in rows.indices) {
            val role = if (r == 0) TextRole.TABLE_HEADER else TextRole.TABLE_CELL
            var h = 0
            var left = 0
            var chars = 0
            val drawn = ArrayList<DrawCell>(columns)
            for (c in 0 until columns) {
                val cell = cells[r][c]
                val inner = maxOf(widths[c] - pad, 1)
                val m = measurer.measureLines(cell.text.ifEmpty { " " }, role, inner, cell.styles)
                h = maxOf(h, m.height)
                chars += cell.text.length
                val leaf = if (cell.text.isEmpty()) null else TextLeaf(cell.text, cell.styles, role, inner)
                drawn.add(DrawCell(leaf, left, widths[c], block.alignments.getOrElse(c) { ColumnAlignment.NONE }))
                left += widths[c]
            }
            h += pad
            val s0 = rowSource(lineStarts, block, r)
            val s1 = rowSourceEnd(lineStarts, block, r)
            out.beginUnit(false)
            out.line(0, h, s0 - top, s1 - top, chars)
            out.draw(LineKind.TABLE_ROW, x, total, table = TableDraw(drawn, total, r == 0, width))
            out.advance(h)
            if (r == 0) {
                out.headerLines = 1
                out.headerHeight = h
            }
        }
    }

    private fun cell(
        row: TableRow,
        column: Int,
    ): Rendered = RenderedText.cell(row.cells.getOrNull(column)?.content ?: emptyList())

    /** Natural widths when they fit; proportional wrapping when the columns stay readable; else unwrapped. */
    private fun columnWidths(
        cells: Array<Array<Rendered>>,
        columns: Int,
        width: Int,
    ): IntArray {
        val pad = 2 * metrics.tableCellPadding
        val natural = IntArray(columns)
        for ((r, row) in cells.withIndex()) {
            val role = if (r == 0) TextRole.TABLE_HEADER else TextRole.TABLE_CELL
            for (c in 0 until columns) {
                natural[c] = maxOf(natural[c], measurer.intrinsicWidth(row[c].text, role, row[c].styles) + pad)
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
