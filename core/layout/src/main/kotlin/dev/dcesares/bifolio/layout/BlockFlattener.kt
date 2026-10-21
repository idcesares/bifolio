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
import dev.dcesares.bifolio.model.TaskState
import dev.dcesares.bifolio.model.ThematicBreak

/**
 * Turns top-level blocks into [BlockLayout]s with a [BlockMeasurer], memoized per (block, width) so a page
 * turn or a re-pagination never measures the same block at the same width twice. Not thread-safe: one
 * instance per pagination run.
 */
@Suppress("TooManyFunctions") // One small function per block kind.
internal class BlockFlattener(
    private val document: Document,
    private val measurer: BlockMeasurer,
    private val geometry: Geometry,
    private val images: ImageSizeProvider = ImageSizeProvider.UNKNOWN,
    private val math: MathSizeProvider = MathSizeProvider.UNKNOWN,
) {
    private val metrics = geometry.metrics
    private val cache = HashMap<Long, BlockLayout>()
    private val tables = TableFlattener(document, measurer, metrics)

    /** The callout whose title line is still to be placed in front of its first content (PAG-05). */
    private var pendingTitle: Callout? = null

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
        pendingTitle = null
        flatten(block, Place(width, 0, block.range.start), out)
        return out.build(keepWithNext = block is Heading)
    }

    /** Where a block goes: the width left for it, its indent in the column and the source offset of its container. */
    private class Place(
        val width: Int,
        val x: Int,
        val top: Int,
    ) {
        fun indented(by: Int): Place = Place(maxOf(width - by, 1), x + by, top)
    }

    private fun flatten(
        block: Block,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        when (block) {
            is Heading ->
                leaf(
                    RenderedText.of(block.content, document.source, block.range.start, block.range.end, markers = true),
                    headingRole(block.level),
                    place,
                    out,
                    keep = true,
                )
            is Paragraph ->
                leaf(
                    RenderedText.of(block.content, document.source, block.range.start, block.range.end, markers = true),
                    TextRole.BODY,
                    place,
                    out,
                    keep = false,
                )
            is CodeBlock -> code(block.code, block, place, out)
            is MermaidFence -> code(block.source, block, place, out)
            is ThematicBreak -> rule(block, place, out)
            is Table -> {
                flushTitle(place, out)
                tables.flatten(block, place.width, place.x, place.top, out)
            }
            is ListBlock -> list(block, place, out)
            is BlockQuote -> quote(block, place, out)
            is Callout -> callout(block, place, out)
            is FootnoteDefinition -> children(block.blocks, place.indented(metrics.quoteIndent), out)
            is HtmlDetails -> details(block, place, out)
            is ImageBlock, is MathBlock, is FrontMatter -> atomic(block, place, out)
        }
    }

    private fun children(
        blocks: List<Block>,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        for ((i, child) in blocks.withIndex()) {
            if (i > 0) out.gap(metrics.innerGap)
            flatten(child, place, out)
        }
    }

    private fun quote(
        block: BlockQuote,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        val first = out.lineCount
        children(block.blocks, place.indented(metrics.quoteIndent), out)
        if (out.lineCount > first) out.bar(BarRec(first, out.lineCount - 1, place.x, BarKind.QUOTE))
    }

    private fun callout(
        block: Callout,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        val first = out.lineCount
        val saved = pendingTitle
        pendingTitle = block
        val inner = place.indented(metrics.quoteIndent)
        children(block.blocks, inner, out)
        flushTitle(inner, out)
        pendingTitle = saved
        if (out.lineCount > first) out.bar(BarRec(first, out.lineCount - 1, place.x, BarKind.CALLOUT))
    }

    private fun list(
        block: ListBlock,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        val inner = place.indented(metrics.listIndent)
        for ((n, item) in block.items.withIndex()) {
            val first = out.lineCount
            for ((i, child) in item.blocks.withIndex()) {
                when {
                    i > 0 -> out.gap(metrics.innerGap)
                    n > 0 -> out.gap(if (block.tight) 0 else metrics.innerGap)
                }
                flatten(child, inner, out)
            }
            if (out.lineCount > first) {
                val kind =
                    when {
                        item.task == TaskState.CHECKED -> MarkerKind.TASK_CHECKED
                        item.task == TaskState.UNCHECKED -> MarkerKind.TASK_UNCHECKED
                        block.ordered -> MarkerKind.NUMBER
                        else -> MarkerKind.BULLET
                    }
                out.marker(MarkerRec(first, place.x, metrics.listIndent, kind, block.startNumber + n))
            }
        }
    }

    private fun details(
        block: HtmlDetails,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        leaf(
            RenderedText.of(block.summary, document.source, block.range.start, block.range.end, markers = true),
            TextRole.BODY,
            place,
            out,
            keep = false,
        )
        if (block.open) children(block.blocks, place.indented(metrics.quoteIndent), out)
    }

    private fun code(
        text: String,
        block: Block,
        place: Place,
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
        val map = CodeSourceMap.build(text, document.source, from, block.range.end)
        val pad = metrics.codePadding
        val inner = Place(maxOf(place.width - 2 * pad, 1), place.x + pad, place.top)
        val language = (block as? CodeBlock)?.takeIf { it.fenced }?.language
        leaf(Rendered(text, map), TextRole.CODE, inner, out, keep = false, free = true, language = language)
    }

    private fun leaf(
        rendered: Rendered,
        role: TextRole,
        place: Place,
        out: BlockLayoutBuilder,
        keep: Boolean,
        free: Boolean = false,
        language: String? = null,
    ) {
        val map = rendered.map
        val w = maxOf(place.width, 1)
        val top = place.top
        val m = measurer.measureLines(rendered.text, role, w, rendered.styles)
        if (m.count == 0) return
        val mapped = map.size > rendered.text.length
        val leaf =
            TextLeaf(
                rendered.text,
                rendered.styles,
                role,
                w,
                sourceStart = if (mapped) map[0] else -1,
                sourceEnd = if (mapped) map[rendered.text.length] else -1,
                language = language,
                targets = rendered.targets,
            )
        val kind = if (role == TextRole.CODE) LineKind.CODE else LineKind.TEXT
        out.beginRun()
        if (!free) out.beginUnit(keep)
        titleLine(place, out, free)
        for (i in 0 until m.count) {
            if (free) out.beginUnit(false)
            val s = m.starts[i]
            val e = m.ends[i]
            val src0 = if (out.isEmpty) 0 else map[s] - top
            val src1 = if (e > s) map[e - 1] + 1 - top else map[e] - top
            out.line(m.tops[i], m.bottoms[i], src0, src1, e - s)
            out.draw(kind, place.x, w, leaf, m.tops[i], m.bottoms[i])
        }
        out.advance(m.bottoms[m.count - 1])
    }

    /** Places the pending callout title as the first line of the run being built. */
    private fun titleLine(
        place: Place,
        out: BlockLayoutBuilder,
        ownUnit: Boolean,
    ) {
        val callout = pendingTitle ?: return
        pendingTitle = null
        val m = measurer.measureLines(TITLE_SAMPLE, TextRole.BODY, maxOf(place.width, 1))
        if (m.count == 0) return
        val h = m.bottoms[0] - m.tops[0]
        if (ownUnit) out.beginUnit(true)
        val src = callout.range.start - place.top
        out.line(0, h, src, src, 0)
        out.draw(LineKind.TITLE, place.x, place.width, callout = callout.kind.ordinal)
        out.advance(h)
    }

    /** A callout whose first content is not text (a table, a rule) still shows its title first. */
    private fun flushTitle(
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        if (pendingTitle == null) return
        out.beginRun()
        titleLine(place, out, ownUnit = true)
    }

    private fun rule(
        block: ThematicBreak,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        flushTitle(place, out)
        out.beginRun()
        out.beginUnit(true)
        out.line(0, metrics.ruleHeight, block.range.start - place.top, block.range.end - place.top, 0)
        out.draw(LineKind.RULE, place.x, place.width)
        out.advance(metrics.ruleHeight)
    }

    /** Images, display math and front matter cards: one line, scaled to the column (PAG-07). */
    private fun atomic(
        block: Block,
        place: Place,
        out: BlockLayoutBuilder,
    ) {
        flushTitle(place, out)
        val width = maxOf(place.width, 1)
        val maxHeight = maxOf(geometry.columnHeight * metrics.atomicMaxHeightPercent / PERCENT, 1)
        val fitted = AtomicFit.fit(naturalSize(block, width), width, maxHeight)
        val h = fitted.height
        val w = fitted.width
        out.beginRun()
        out.beginUnit(true)
        out.line(0, h, block.range.start - place.top, block.range.end - place.top, 0)
        out.draw(LineKind.ATOMIC, place.x + (width - w) / 2, w, atom = block)
        out.advance(h)
    }

    /** An image or formula with a known size uses it; everything else, placeholders too, comes from the measurer. */
    private fun naturalSize(
        block: Block,
        width: Int,
    ): AtomicSize {
        val known: AtomicSize? =
            when (block) {
                is ImageBlock -> (images.sizeOf(block) as? ImageSize.Known)?.let { AtomicSize(it.width, it.height) }
                is MathBlock -> (math.sizeOf(block) as? MathSize.Known)?.let { AtomicSize(it.width, it.height) }
                else -> null
            }
        return known ?: measurer.measureAtomic(block, width)
    }

    private companion object {
        const val WIDTH_BITS = 24
        const val PERCENT = 100

        /** Longest callout label, only used to know the height of the title line. */
        const val TITLE_SAMPLE = "Important"

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
