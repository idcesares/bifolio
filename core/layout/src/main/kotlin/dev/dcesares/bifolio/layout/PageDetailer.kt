package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.Document

/**
 * Turns a [Page] into drawing instructions, reusing the lines the paginator counted so the reader draws
 * exactly what was paginated and never breaks text again (ADR-0013). Vertical positions follow
 * [PageComposer]: a block gap before each block that starts after other content, a repeated table header
 * on continuation slices.
 *
 * Call it off the main thread; it measures a block the first time it sees it at a given width. Calls are
 * serialized, so one instance can be shared by the prefetching code.
 */
public class PageDetailer(
    private val document: Document,
    measurer: BlockMeasurer,
    private val geometry: Geometry,
) {
    private val flattener = BlockFlattener(document, measurer, geometry)

    /** Drawing instructions of [page] placed in column [column] (its width decides the line breaks). */
    @Synchronized
    public fun detail(
        page: Page,
        column: Int,
    ): PageDraw {
        val width = geometry.columnWidths[column.coerceIn(0, geometry.columnWidths.lastIndex)]
        val items = ArrayList<DrawItem>()
        var used = 0
        for (slice in page.slices) {
            val layout = flattener.layout(slice.blockIndex, width)
            val s = slice.startLine
            if (used > 0 && s == 0) used += geometry.metrics.blockGap
            var base = used
            val header = slice.repeatedHeaderHeight
            if (header > 0) {
                tableRow(slice, layout, 0, base - layout.tops[0], items)
                base += header
            }
            val offset = base - layout.tops[s]
            if (s > 0) continuation(layout, s, base, items)
            sliceItems(slice, layout, offset, items)
            used += header + layout.height(s, slice.endLine)
        }
        return PageDraw(items, width, used, page.endsDocument)
    }

    private fun continuation(
        layout: BlockLayout,
        s: Int,
        y: Int,
        items: MutableList<DrawItem>,
    ) {
        val kind = layout.draw.kind[s]
        if (kind == LineKind.CODE || kind == LineKind.TABLE_ROW) {
            items.add(DrawContinuation(layout.draw.x[s], y, layout.bottoms[s] - layout.tops[s]))
        }
    }

    private fun sliceItems(
        slice: Slice,
        layout: BlockLayout,
        offset: Int,
        items: MutableList<DrawItem>,
    ) {
        val d = layout.draw
        val s = slice.startLine
        val e = slice.endLine
        for (bar in d.bars) {
            val first = maxOf(bar.firstLine, s)
            val last = minOf(bar.lastLine, e - 1)
            if (first > last) continue
            val top = layout.tops[first] + offset
            items.add(DrawBar(bar.x, top, layout.bottoms[last] + offset - top, bar.kind))
        }
        var i = s
        while (i < e) {
            when (d.kind[i]) {
                LineKind.TEXT, LineKind.CODE -> i = textRun(layout, i, e, offset, items)
                LineKind.TABLE_ROW -> {
                    tableRow(slice, layout, i, offset, items)
                    i++
                }
                LineKind.TITLE -> {
                    val kind = CalloutKind.entries[d.callout[i]]
                    items.add(
                        DrawCalloutTitle(kind, d.x[i], layout.tops[i] + offset, layout.bottoms[i] - layout.tops[i]),
                    )
                    i++
                }
                LineKind.RULE -> {
                    items.add(DrawRule(d.x[i], d.width[i], layout.tops[i] + offset, layout.bottoms[i] - layout.tops[i]))
                    i++
                }
                else -> {
                    val block = document.blocks[slice.blockIndex]
                    val y = layout.tops[i] + offset
                    items.add(DrawAtomic(block, d.x[i], d.width[i], y, layout.bottoms[i] - layout.tops[i]))
                    i++
                }
            }
        }
        for (m in d.markers) {
            if (m.line < s || m.line >= e) continue
            items.add(
                DrawMarker(
                    m.x,
                    m.width,
                    layout.tops[m.line] + offset,
                    layout.bottoms[m.line] - layout.tops[m.line],
                    m.kind,
                    m.number,
                ),
            )
        }
    }

    /** Emits one [DrawText] for consecutive lines of the same leaf starting at [from]; returns the next line. */
    private fun textRun(
        layout: BlockLayout,
        from: Int,
        end: Int,
        offset: Int,
        items: MutableList<DrawItem>,
    ): Int {
        val d = layout.draw
        val leaf = d.leaf[from]!!
        val kind = d.kind[from]
        val pad = geometry.metrics.codePadding
        var to = from + 1
        while (to < end && d.leaf[to] === leaf && d.kind[to] == kind) to++
        val y = layout.tops[from] + offset
        val height = layout.bottoms[to - 1] + offset - y
        if (kind == LineKind.CODE) items.add(DrawCodeSurface(d.x[from] - pad, d.width[from] + 2 * pad, y, height))
        items.add(DrawText(leaf, d.x[from], y, height, d.leafTop[from], d.leafBottom[to - 1]))
        return to
    }

    private fun tableRow(
        slice: Slice,
        layout: BlockLayout,
        line: Int,
        offset: Int,
        items: MutableList<DrawItem>,
    ) {
        val d = layout.draw
        val table = d.table[line] ?: return
        items.add(
            DrawTableRow(
                slice.blockIndex,
                table.header,
                table.cells,
                d.x[line],
                table.totalWidth,
                table.available,
                layout.tops[line] + offset,
                layout.bottoms[line] - layout.tops[line],
            ),
        )
    }
}
