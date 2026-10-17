package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Measurer wrapper that counts calls and characters measured. */
private class CountingMeasurer(
    private val inner: BlockMeasurer = FakeBlockMeasurer(),
) : BlockMeasurer {
    var lineCalls = 0
    var chars = 0L

    override fun measureLines(
        text: String,
        role: TextRole,
        widthPx: Int,
        styles: TextStyles,
    ): LineMetrics {
        lineCalls++
        chars += text.length
        return inner.measureLines(text, role, widthPx, styles)
    }

    override fun intrinsicWidth(
        text: String,
        role: TextRole,
        styles: TextStyles,
    ): Int = inner.intrinsicWidth(text, role, styles)

    override fun measureAtomic(
        block: Block,
        widthPx: Int,
    ): AtomicSize = inner.measureAtomic(block, widthPx)
}

/** Complexity guards for PRF-04: measuring and pagination must stay linear in the size of the document. */
class PaginationComplexityTest {
    private fun corpus(
        seed: Int,
        words: Int,
    ): String {
        val rng = Random(seed)
        val vocab =
            "the of and to in that is was for with as his on be at by this had not are but from or have".split(
                ' ',
            )
        val out = StringBuilder()
        var count = 0
        var chapter = 0

        fun sentence() = (1..rng.nextInt(8, 22)).joinToString(" ") { vocab[rng.nextInt(vocab.size)] } + "."

        fun paragraph() = (1..rng.nextInt(3, 8)).joinToString(" ") { sentence() }
        while (count < words) {
            out.append("# Chapter ${++chapter}\n\n")
            repeat(rng.nextInt(12, 30)) {
                when (rng.nextInt(8)) {
                    0 -> out.append(List(rng.nextInt(2, 6)) { "- ${sentence()}" }.joinToString("\n")).append("\n\n")
                    1 -> out.append("> ${paragraph()}\n\n")
                    3 ->
                        out
                            .append("```\n")
                            .append(List(rng.nextInt(4, 14)) { "let v$it = $it" }.joinToString("\n"))
                            .append("\n```\n\n")
                    else -> out.append(paragraph()).append("\n\n")
                }
                count += 80
            }
        }
        return out.toString()
    }

    private fun run(
        doc: Document,
        measurer: CountingMeasurer,
    ): Int =
        Paginator(doc, measurer, testGeometry(lines = 30, columns = 1, columnWidth = 400)).paginateAll()!!.pages.size

    @Test
    fun `doubling the document at most doubles measurements`() {
        val small = MarkdownParser.parse(corpus(7, 20_000))
        val large = MarkdownParser.parse(corpus(7, 40_000))
        val a = CountingMeasurer()
        val b = CountingMeasurer()
        run(small, a)
        run(large, b)
        assertTrue(b.lineCalls <= a.lineCalls * 2.2, { "calls ${a.lineCalls} -> ${b.lineCalls}" })
        assertTrue(b.chars <= a.chars * 2.2, { "chars ${a.chars} -> ${b.chars}" })
    }

    @Test
    fun `pagination measures every block exactly once per geometry`() {
        val doc = MarkdownParser.parse(corpus(3, 5_000))
        val geometry = testGeometry(lines = 30, columns = 1, columnWidth = 400)
        // Reference: one layout per block, nothing else.
        val reference = CountingMeasurer()
        val flattener = BlockFlattener(doc, reference, geometry)
        for (i in doc.blocks.indices) flattener.layout(i, 400)
        val paginated = CountingMeasurer()
        Paginator(doc, paginated, geometry).paginateAll()
        assertEquals(reference.lineCalls, paginated.lineCalls, "paginateAll measures each block once")
        assertEquals(reference.chars, paginated.chars)
        // Anchored pagination (provisional window, then canonical pass) shares the same cache.
        val anchored = CountingMeasurer()
        Paginator(doc, anchored, geometry).paginateAround(Anchor(doc.blocks.size / 2, 0))
        assertEquals(reference.lineCalls, anchored.lineCalls, "paginateAround measures each block once")
    }

    @Test
    fun `detailing a page re-measures only the blocks on that page`() {
        val doc = MarkdownParser.parse(corpus(3, 5_000))
        val geometry = testGeometry(lines = 30, columns = 1, columnWidth = 400)
        val counting = CountingMeasurer()
        val flow = Paginator(doc, counting, geometry).paginateAll()!!
        val before = counting.lineCalls
        val detailer = PageDetailer(doc, counting, geometry)
        val page = flow.pages[flow.pages.size / 2]
        detailer.detail(page, 0)
        detailer.detail(page, 0)
        val blocksOnPage =
            page.slices
                .map { it.blockIndex }
                .distinct()
                .size
        assertTrue(counting.lineCalls - before <= blocksOnPage * 4, "detail measured ${counting.lineCalls - before}")
    }
}
