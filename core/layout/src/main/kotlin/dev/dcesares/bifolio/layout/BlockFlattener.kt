package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.model.MermaidFence
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.ThematicBreak
import dev.dcesares.bifolio.model.plainText

/**
 * Turns top-level blocks into [BlockLayout]s with a [BlockMeasurer], memoized per (block, width) so a page
 * turn or a re-pagination never measures the same block at the same width twice. Not thread-safe: one
 * instance per pagination run.
 */
internal class BlockFlattener(
    private val document: Document,
    private val measurer: BlockMeasurer,
    private val geometry: Geometry,
) {
    private val metrics = geometry.metrics
    private val cache = HashMap<Long, BlockLayout>()
    private val tables = TableFlattener(document, measurer, metrics)

    val blockCount: Int get() = document.blocks.size

    fun layout(
        blockIndex: Int,
        width: Int,
    ): BlockLayout {
        val key = (blockIndex.toLong() shl WIDTH_BITS) or width.toLong()
        return cache.getOrPut(key) { build(blockIndex, width) }
    }

    private fun build(
        blockIndex: Int,
        width: Int,
    ): BlockLayout {
        val block = document.blocks[blockIndex]
        val out = BlockLayoutBuilder()
        flatten(block, width, block.range.start, out)
        return out.build(keepWithNext = block is Heading)
    }

    private fun flatten(
        block: Block,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        when (block) {
            is Heading -> leaf(plainText(block.content), headingRole(block.level), width, block, top, out, keep = true)
            is Paragraph -> leaf(plainText(block.content), TextRole.BODY, width, block, top, out, keep = false)
            is CodeBlock -> code(block.code, block, width, top, out)
            is MermaidFence -> code(block.source, block, width, top, out)
            is ThematicBreak -> rule(block, top, out)
            is Table -> tables.flatten(block, width, top, out)
            is ListBlock -> list(block, width, top, out)
            is BlockQuote -> children(block.blocks, width - metrics.quoteIndent, top, out)
            is Callout -> children(block.blocks, width - metrics.quoteIndent, top, out)
            is FootnoteDefinition -> children(block.blocks, width - metrics.quoteIndent, top, out)
            is HtmlDetails -> details(block, width, top, out)
            is ImageBlock, is MathBlock, is FrontMatter -> atomic(block, width, top, out)
        }
    }

    private fun children(
        blocks: List<Block>,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        val w = maxOf(width, 1)
        for ((i, child) in blocks.withIndex()) {
            if (i > 0) out.gap(metrics.innerGap)
            flatten(child, w, top, out)
        }
    }

    private fun list(
        block: ListBlock,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        val w = maxOf(width - metrics.listIndent, 1)
        for ((n, item) in block.items.withIndex()) {
            for ((i, child) in item.blocks.withIndex()) {
                when {
                    i > 0 -> out.gap(metrics.innerGap)
                    n > 0 -> out.gap(if (block.tight) 0 else metrics.innerGap)
                }
                flatten(child, w, top, out)
            }
        }
    }

    private fun details(
        block: HtmlDetails,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        leaf(plainText(block.summary), TextRole.BODY, width, block, top, out, keep = false)
        if (block.open) children(block.blocks, width - metrics.quoteIndent, top, out)
    }

    private fun code(
        text: String,
        block: Block,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        val fenced = (block as? CodeBlock)?.fenced ?: true
        val from =
            if (fenced) {
                val nl = document.source.indexOf('\n', block.range.start)
                if (nl in 0 until block.range.end) nl + 1 else block.range.start
            } else {
                block.range.start
            }
        leaf(text, TextRole.CODE, width, block, top, out, keep = false, free = true, searchFrom = from)
    }

    private fun leaf(
        text: String,
        role: TextRole,
        width: Int,
        block: Block,
        top: Int,
        out: BlockLayoutBuilder,
        keep: Boolean,
        free: Boolean = false,
        searchFrom: Int = block.range.start,
    ) {
        val m = measurer.measureLines(text, role, maxOf(width, 1))
        if (m.count == 0) return
        out.beginRun()
        val map = OffsetMap.build(text, document.source, searchFrom, block.range.end)
        if (!free) out.beginUnit(keep)
        for (i in 0 until m.count) {
            if (free) out.beginUnit(false)
            val s = m.starts[i]
            val e = m.ends[i]
            val src0 = if (out.isEmpty) 0 else map[s] - top
            val src1 = if (e > s) map[e - 1] + 1 - top else map[e] - top
            out.line(m.tops[i], m.bottoms[i], src0, src1, e - s)
        }
        out.advance(m.bottoms[m.count - 1])
    }

    private fun rule(
        block: ThematicBreak,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        out.beginRun()
        out.beginUnit(true)
        out.line(0, metrics.ruleHeight, block.range.start - top, block.range.end - top, 0)
        out.advance(metrics.ruleHeight)
    }

    /** Images, display math and front matter cards: one line, scaled to the column (PAG-07). */
    private fun atomic(
        block: Block,
        width: Int,
        top: Int,
        out: BlockLayoutBuilder,
    ) {
        val size = measurer.measureAtomic(block, maxOf(width, 1))
        val maxHeight = maxOf(geometry.columnHeight * metrics.atomicMaxHeightPercent / PERCENT, 1)
        var scale = 1.0
        if (size.width > width) scale = width.toDouble() / size.width
        if (size.height * scale > maxHeight) scale = maxHeight.toDouble() / size.height
        val h = maxOf(kotlin.math.ceil(size.height * scale).toInt(), 1)
        out.beginRun()
        out.beginUnit(true)
        out.line(0, h, block.range.start - top, block.range.end - top, 0)
        out.advance(h)
    }

    private companion object {
        const val WIDTH_BITS = 24
        const val PERCENT = 100

        val HEADING_ROLES =
            arrayOf(
                TextRole.HEADING_1,
                TextRole.HEADING_2,
                TextRole.HEADING_3,
                TextRole.HEADING_4,
                TextRole.HEADING_5,
                TextRole.HEADING_6,
            )

        fun headingRole(level: Int): TextRole = HEADING_ROLES[(level - 1).coerceIn(0, HEADING_ROLES.lastIndex)]
    }
}
