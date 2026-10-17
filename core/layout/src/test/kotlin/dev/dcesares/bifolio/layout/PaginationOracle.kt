package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.MermaidFence
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.plainText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Independent checker of a [PageFlow]. It restates the rules from SPEC.md in its own code and only reuses the
 * flattened lines (heights, units) as input data, never the composer's decisions.
 *
 * Feasibility condition, fixed before looking at any failure: PAG-03 is asserted for a heading only when the
 * heading, the gap and the lines it must carry fit one empty page. PAG-02 and the height bound are asserted
 * everywhere, with exactly two exemptions, each tied to a [PageComposer.next] empty-page fallback. Both were
 * found by running the property (table rows of 16 lines at narrow widths) and derived from the failures; they
 * were not part of the first draft:
 * - a page may exceed the column height only if it holds a single line, taller than an empty column;
 * - a table header may be left alone only if its slice opens the page and header plus first row exceed a column.
 */
internal class PaginationOracle(
    private val doc: Document,
    measurer: BlockMeasurer,
    private val geometry: Geometry,
    private val label: String,
) {
    private val flattener = BlockFlattener(doc, measurer, geometry)
    private val width = geometry.columnWidths[0]
    var headingsChecked = 0
        private set
    var headingsSkipped = 0
        private set
    var overflows = 0
        private set

    private fun layout(block: Int) = flattener.layout(block, width)

    fun check(flow: PageFlow) {
        checkCoverage(flow)
        checkPages(flow)
        checkSplits(flow)
        checkKeepWithNext(flow)
        checkIdentity(flow)
    }

    /** PAG-02/PAG-11: every flattened line of every block appears on exactly one page, in order. */
    private fun checkCoverage(flow: PageFlow) {
        var block = 0
        var line = 0
        var chars = 0L
        for ((p, page) in flow.pages.withIndex()) {
            assertTrue(page.slices.isNotEmpty(), "$label page $p is empty")
            for (slice in page.slices) {
                while (block < doc.blocks.size && line >= layout(block).lineCount) {
                    block++
                    line = 0
                }
                assertEquals(block, slice.blockIndex, "$label page $p slice $slice: expected block $block line $line")
                assertEquals(line, slice.startLine, "$label page $p slice $slice: expected line $line")
                val l = layout(block)
                assertTrue(slice.endLine <= l.lineCount, "$label slice $slice past ${l.lineCount} lines")
                for (i in slice.startLine until slice.endLine) chars += l.chars[i]
                line = slice.endLine
            }
        }
        while (block < doc.blocks.size && line >= layout(block).lineCount) {
            block++
            line = 0
        }
        assertEquals(doc.blocks.size, block, "$label: lines lost at the end of the document")
        assertEquals(doc.blocks.sumOf { renderedLength(it) }.toLong(), chars, "$label: characters lost or duplicated")
    }

    private fun checkPages(flow: PageFlow) {
        for ((p, page) in flow.pages.withIndex()) {
            var used = 0
            for ((i, s) in page.slices.withIndex()) {
                if (i > 0 && s.startLine == 0) used += geometry.metrics.blockGap
                used += s.height
            }
            assertEquals(used, page.usedHeight, "$label page $p used height")
            if (page.usedHeight > geometry.columnHeight) {
                // The only infeasible case allowed: one line (a table row with wrapped cells) taller than an
                // empty column is placed alone and overflows (PageComposer fallback).
                val only = page.slices.singleOrNull()
                assertTrue(only != null && only.lineCount == 1, "$label page $p overflows with ${page.slices}")
                overflows++
            }
            assertEquals(p == flow.pages.lastIndex, page.endsDocument, "$label page $p endsDocument")
            val blocks = page.slices.map { it.blockIndex }
            assertEquals(blocks.sorted(), blocks, "$label page $p blocks out of order")
        }
    }

    /** PAG-02, PAG-06, SYN-15: breaks inside a block. */
    private fun checkSplits(flow: PageFlow) {
        val all = flow.pages.flatMap { it.slices }
        val opensPage = flow.pages.map { it.slices.first() }.toSet()
        for ((i, a) in all.withIndex()) {
            val l = layout(a.blockIndex)
            if (a.startLine > 0 && l.headerLines > 0) {
                assertEquals(l.headerHeight, a.repeatedHeaderHeight, "$label table continuation repeats the header")
                val body = l.height(a.startLine, a.endLine)
                assertEquals(a.repeatedHeaderHeight + body, a.height, "$label header counted in height")
            }
            if (a.endLine < l.lineCount) checkBreak(l, a, all[i + 1], a in opensPage)
        }
    }

    private fun checkBreak(
        l: BlockLayout,
        a: Slice,
        b: Slice,
        opensPage: Boolean,
    ) {
        assertEquals(a.blockIndex, b.blockIndex, "$label $a must be continued by the next slice")
        assertEquals(a.endLine, b.startLine, "$label $a $b")
        val k = a.endLine
        if (l.headerLines > 0 && a.startLine == 0) {
            // Exempt only through the empty-page fallback: the slice opens its page and the header with its
            // first row cannot share an empty column. On a page with content the table must move instead.
            val infeasible = opensPage && l.height(0, l.headerLines + 1) > geometry.columnHeight
            assertTrue(k > l.headerLines || infeasible, "$label table header left alone: $a")
        }
        val u = l.units.of[k]
        val first = l.units.first[u]
        if (first == k) return
        val len = l.units.length[u]
        assertTrue(len >= MIN_SPLITTABLE && !l.units.keep[u], "$label unit of $len lines split at $k in $a")
        assertTrue(k - maxOf(first, a.startLine) >= MIN_SIDE, "$label orphan: $a")
        assertTrue(first + len - k >= MIN_SIDE, "$label widow: $a")
    }

    /** PAG-03. */
    private fun checkKeepWithNext(flow: PageFlow) {
        for (page in flow.pages) {
            for ((i, s) in page.slices.withIndex()) {
                val next = if (doc.blocks[s.blockIndex] is Heading) nextBlockWithLines(s.blockIndex) else -1
                if (next >= 0) checkHeading(page, i, next)
            }
        }
    }

    private fun checkHeading(
        page: Page,
        index: Int,
        next: Int,
    ) {
        val heading = page.slices[index].blockIndex
        if (!feasible(heading)) {
            headingsSkipped++
            return
        }
        headingsChecked++
        val following = page.slices.getOrNull(index + 1)
        assertTrue(following != null, "$label heading $heading ends a page with block $next still to come")
        assertEquals(next, following!!.blockIndex, "$label heading $heading is followed by block $next")
        if (doc.blocks[next] !is Heading) {
            val need = minOf(MIN_SIDE, layout(next).lineCount)
            assertTrue(following.lineCount >= need, "$label heading $heading has ${following.lineCount} lines after it")
        }
    }

    /** Heading chain, gaps and the lines that must follow fit an empty page. */
    private fun feasible(heading: Int): Boolean {
        var total = 0
        var i = heading
        var first = true
        while (i >= 0) {
            val l = layout(i)
            total += (if (first) 0 else geometry.metrics.blockGap) + l.height(0, l.lineCount)
            first = false
            if (doc.blocks[i] !is Heading) {
                // Replace the whole block by the lines it must contribute.
                total -= l.height(0, l.lineCount)
                total += l.height(0, minOf(MIN_SIDE, l.lineCount).let { e -> leadOf(l, e) })
                break
            }
            i = nextBlockWithLines(i)
        }
        return total <= geometry.columnHeight
    }

    private fun leadOf(
        l: BlockLayout,
        from: Int,
    ): Int {
        var e = from
        while (e < l.lineCount) {
            val u = l.units.of[e]
            val atBoundary = l.units.first[u] == e && !(l.headerLines > 0 && e <= l.headerLines)
            if (atBoundary) break
            e++
        }
        return e
    }

    private fun nextBlockWithLines(from: Int): Int {
        var i = from + 1
        while (i < doc.blocks.size && layout(i).lineCount == 0) i++
        return if (i < doc.blocks.size) i else -1
    }

    /** PAG-11. */
    private fun checkIdentity(flow: PageFlow) {
        for ((i, page) in flow.pages.withIndex()) {
            if (i > 0) {
                assertTrue(
                    flow.pages[i - 1].anchor.compareReadingOrder(page.anchor) < 0,
                    "$label page $i anchor ${page.anchor} does not follow ${flow.pages[i - 1].anchor}",
                )
            }
            assertEquals(i, flow.pageIndexOf(page.anchor), "$label page identity of page $i")
        }
    }

    private fun renderedLength(b: Block): Int =
        when (b) {
            is Heading -> plainText(b.content).length
            is Paragraph -> plainText(b.content).length
            is CodeBlock -> b.code.length
            is MermaidFence -> b.source.length
            is Table -> tableLength(b)
            is ListBlock -> b.items.sumOf { item -> item.blocks.sumOf { renderedLength(it) } }
            is BlockQuote -> b.blocks.sumOf { renderedLength(it) }
            is Callout -> b.blocks.sumOf { renderedLength(it) }
            is FootnoteDefinition -> b.blocks.sumOf { renderedLength(it) }
            is HtmlDetails -> plainText(b.summary).length + if (b.open) b.blocks.sumOf { renderedLength(it) } else 0
            else -> 0
        }

    private fun tableLength(t: Table): Int =
        (listOf(t.header) + t.rows).sumOf { row ->
            (0 until t.columnCount).sumOf { c -> row.cells.getOrNull(c)?.let { plainText(it.content).length } ?: 0 }
        }

    private companion object {
        const val MIN_SIDE = 2
        const val MIN_SPLITTABLE = 4
    }
}
