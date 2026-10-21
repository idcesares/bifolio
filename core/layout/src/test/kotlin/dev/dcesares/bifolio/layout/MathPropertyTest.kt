package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * SYN-12 and PAG-07 as properties: with display formulas of every size, inline formulas and currency text among
 * random blocks, the coverage oracle (PAG-02, PAG-03, PAG-06 and "every character once") still holds, every
 * drawn formula box fits its column and 85 percent of the page, a formula that starts a page did not fit the page
 * before it, and the anchor survives a change of geometry (ANC-03). Fixed seeds; every message carries the case.
 */
class MathPropertyTest {
    private fun prose(block: String): String = if (block.startsWith("#") || block.startsWith("|")) "plain" else block

    /** Markdown of [count] blocks, about a third of them math in one of its four forms. */
    private fun markdownWithMath(
        seed: Long,
        count: Int,
    ): String {
        val random = Random(seed)
        val text = RandomMarkdown(seed)
        val parts = ArrayList<String>()
        repeat(count) { k ->
            parts.add(
                when (random.nextInt(MATH_ONE_IN * 2)) {
                    0 -> "\$\$f_$k = \\frac{$k}{2}\$\$"
                    1 -> "```math\ng_$k\n```"
                    2 -> "\$\$\n\\begin{aligned} a_$k &= b \\\\ c &= d \\end{aligned}\n\$\$"
                    3 -> "${prose(text.block())} \$x_$k^2\$ end"
                    4 -> "It costs \$5 and \$10 today, block $k, between \$5-\$10."
                    else -> text.block()
                },
            )
        }
        return parts.joinToString("\n\n") + "\n"
    }

    /** A fixed, immutable answer per formula: unknown, tiny, wide, tall, huge or ordinary. */
    private fun providerFor(seed: Long): MathSizeProvider =
        MathSizeProvider { block ->
            val digits = block.tex.filter { it.isDigit() }
            val k = digits.take(DIGITS).toIntOrNull() ?: 0
            val r = Random(seed * 31 + k)
            when (r.nextInt(6)) {
                0 -> MathSize.Unknown
                1 -> MathSize.Known(r.nextInt(1, 60), r.nextInt(1, 60))
                2 -> MathSize.Known(r.nextInt(1000, 6000), r.nextInt(50, 400))
                3 -> MathSize.Known(r.nextInt(50, 400), r.nextInt(1000, 9000))
                4 -> MathSize.Known(r.nextInt(2000, 8000), r.nextInt(2000, 8000))
                else -> MathSize.Known(r.nextInt(100, 700), r.nextInt(100, 700))
            }
        }

    private fun checkRound(
        doc: Document,
        provider: MathSizeProvider,
        geometry: Geometry,
        measurer: FakeBlockMeasurer,
        tag: String,
    ): Int {
        val flow = Paginator(doc, measurer, geometry, math = provider).paginateAll()!!
        PaginationOracle(doc, measurer, geometry, tag, math = provider).check(flow)
        val detailer = PageDetailer(doc, measurer, geometry, math = provider)
        val cap = maxOf(geometry.columnHeight * geometry.metrics.atomicMaxHeightPercent / PERCENT, 1)
        val equal = geometry.columnWidths.distinct().size == 1
        var pushed = 0
        for ((p, page) in flow.pages.withIndex()) {
            val column = p % geometry.columnCount
            val width = geometry.columnWidths[column]
            val draw = detailer.detail(page, column)
            val atoms = draw.items.filterIsInstance<DrawAtomic>().filter { it.block is MathBlock }
            for (a in atoms) {
                val inside = a.width in 1..width && a.x >= 0 && a.x + a.width <= width
                assertTrue(inside, "$tag page $p box ${a.x}+${a.width}/$width")
                assertTrue(a.height in 1..cap, "$tag page $p box height ${a.height} over cap $cap")
                assertTrue(a.y >= 0 && a.y + a.height <= draw.usedHeight, "$tag page $p box y=${a.y} h=${a.height}")
            }
            val first = page.slices.first()
            val startsMath = first.startLine == 0 && doc.blocks[first.blockIndex] is MathBlock
            if (equal && p > 0 && startsMath) {
                val needed = flow.pages[p - 1].usedHeight + geometry.metrics.blockGap + atoms.first().height
                assertTrue(needed > geometry.columnHeight, "$tag page $p: the formula would have fitted ($needed px)")
                pushed++
            }
        }
        return pushed
    }

    private fun anchorsSurvive(
        doc: Document,
        provider: MathSizeProvider,
        a: Geometry,
        b: Geometry,
        measurer: FakeBlockMeasurer,
        random: Random,
        tag: String,
    ) {
        val flowA = Paginator(doc, measurer, a, math = provider).paginateAll()!!
        repeat(ANCHORS) {
            val anchor = flowA.pages[random.nextInt(flowA.pages.size)].anchor
            val seen = ArrayList<PageFlow>()
            Paginator(doc, measurer, b, math = provider).paginateAround(anchor, onProgress = { seen.add(it) })
            for (flow in seen) {
                val page = flow.pages[flow.pageIndexOf(anchor)]
                assertTrue(page.slices.any { it.blockIndex == anchor.blockIndex }, "$tag $anchor not on its page")
            }
        }
    }

    private fun mathBlocks(blocks: List<Block>): Int = blocks.count { it is MathBlock }

    @Test
    fun `random documents with formulas of every size keep the oracle, the boxes and the anchors`() {
        val random = Random(SEED)
        var blocksSeen = 0
        var inlineSeen = 0
        var pushes = 0
        repeat(DOCUMENTS) { n ->
            val seed = SEED + n
            val doc = MarkdownParser.parse(markdownWithMath(seed, BLOCKS))
            blocksSeen += mathBlocks(doc.blocks)
            val paragraphs = doc.blocks.filterIsInstance<Paragraph>()
            inlineSeen += paragraphs.sumOf { p -> p.content.count { it is Inline.InlineMath } }
            val provider = providerFor(seed)
            val measurer = FakeBlockMeasurer(charWidth = random.nextInt(6, 13), lineHeight = 20)
            repeat(ROUNDS) { r ->
                val g = if (r % 2 == 0) RandomGeometries.equal(random) else RandomGeometries.mixed(random)
                val tag = "seed=$seed round=$r cols=${g.columnWidths} lines=${g.columnHeight / 20}"
                pushes += checkRound(doc, provider, g, measurer, tag)
                anchorsSurvive(doc, provider, g, RandomGeometries.any(random), measurer, random, tag)
            }
        }
        assertTrue(blocksSeen > DOCUMENTS, "the property would be vacuous: $blocksSeen display blocks")
        assertTrue(inlineSeen > DOCUMENTS, "no inline math generated: $inlineSeen")
        assertTrue(pushes > 0, "no formula was ever pushed to the next page")
    }

    @Test
    fun `currency text in random documents never becomes math`() {
        repeat(DOCUMENTS) { n ->
            val doc = MarkdownParser.parse(markdownWithMath(SEED + n, BLOCKS))
            for (p in doc.blocks.filterIsInstance<Paragraph>()) {
                if (plainText(p.content).contains("It costs")) {
                    assertTrue(p.content.none { it is Inline.InlineMath }, "seed ${SEED + n}: $p")
                    assertEquals(1, p.content.size, "one merged text run: ${p.content}")
                }
            }
        }
    }

    @Test
    fun `C-14 and C-15 satisfy the oracle at random geometries with random sizes`() {
        val random = Random(SEED)
        for (name in listOf("math-inline-display.md", "math-errors.md")) {
            val doc = CorpusFiles.load(name)
            assertTrue(mathBlocks(doc.blocks) >= 10, "$name has display math")
            repeat(CORPUS_ROUNDS) { r ->
                val g = RandomGeometries.any(random)
                val measurer = FakeBlockMeasurer(charWidth = random.nextInt(6, 13), lineHeight = 20)
                checkRound(doc, providerFor(SEED + r), g, measurer, "$name round $r cols=${g.columnWidths}")
            }
        }
    }

    private companion object {
        const val SEED = 20261021L
        const val DOCUMENTS = 40
        const val ROUNDS = 6
        const val BLOCKS = 45
        const val ANCHORS = 3
        const val CORPUS_ROUNDS = 60
        const val MATH_ONE_IN = 3
        const val PERCENT = 100
        const val DIGITS = 4
    }
}
