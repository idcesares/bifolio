package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Block
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** PAG-09 anchored lazy pagination, PAG-10 cache, PAG-11 page identity, ANC-03 anchor on the visible spread. */
class AnchoredPaginationTest {
    private class CountingMeasurer(
        private val inner: BlockMeasurer = FakeBlockMeasurer(),
    ) : BlockMeasurer by inner {
        var textCalls = 0

        override fun measureLines(
            text: String,
            role: TextRole,
            widthPx: Int,
            styles: TextStyles,
        ): LineMetrics {
            textCalls++
            return inner.measureLines(text, role, widthPx, styles)
        }

        override fun measureAtomic(
            block: Block,
            widthPx: Int,
        ): AtomicSize = inner.measureAtomic(block, widthPx)
    }

    private fun manyParagraphs(count: Int) = markdown(*Array(count) { para(5, letter = 'a' + it % 26) })

    @Test
    fun `PAG-11 page identity is the anchor of its first slice and anchors strictly increase`() {
        val (_, flow) = paginate(markdown(para(25)))
        assertEquals(listOf(Anchor(0, 0), Anchor(0, 200), Anchor(0, 400)), flow.pages.map { it.anchor })
        flow.pages.forEachIndexed { i, page -> assertEquals(i, flow.pageIndexOf(page.anchor)) }
    }

    @Test
    fun `an anchor inside a line is shown on the page that holds the line`() {
        val flow = paginate(markdown(para(25))).second
        assertEquals(1, flow.pageIndexOf(Anchor(0, 250)))
        assertEquals(1, flow.pageIndexOf(Anchor(0, 399)))
        assertEquals(2, flow.pageIndexOf(Anchor(0, 400)))
    }

    @Test
    fun `an anchor past the end clamps to the last page and an empty flow has no page`() {
        val flow = paginate(markdown(para(25))).second
        assertEquals(2, flow.pageIndexOf(Anchor(7, 0)))
        assertEquals(-1, paginate("").second.pageIndexOf(Anchor(0, 0)))
    }

    @Test
    fun `PAG-09 delivers a provisional window at the anchor, then the canonical flow, then the complete one`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(60))
        val geometry = testGeometry(columns = 2)
        val anchor = Anchor(30, 0)
        val seen = ArrayList<PageFlow>()
        val result =
            Paginator(
                doc,
                FakeBlockMeasurer(),
                geometry,
            ).paginateAround(anchor, onProgress = { seen.add(it) })!!

        assertTrue(seen.size >= 3, "provisional, canonical window and complete expected, got ${seen.size}")
        val provisional = seen.first()
        assertTrue(provisional.provisional && !provisional.complete)
        assertEquals(0, provisional.spreadIndexOf(anchor), "the anchor is on the first provisional spread")
        assertEquals(3 * geometry.columnCount, provisional.pages.size)

        val canonical = seen[1]
        assertTrue(!canonical.provisional && !canonical.complete)
        val spread = canonical.spreadIndexOf(anchor)
        assertTrue(canonical.spreadCount >= spread + 3, "at least two spreads after the anchor spread")

        assertSame(result, seen.last())
        assertTrue(result.complete)
        assertEquals(Paginator(doc, FakeBlockMeasurer(), geometry).paginateAll()!!.pages, result.pages)
    }

    @Test
    fun `PAG-09 the anchor block starts the provisional window`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(60))
        var first: PageFlow? = null
        Paginator(doc, FakeBlockMeasurer(), testGeometry()).paginateAround(
            Anchor(30, 0),
            cancel = { first != null },
            onProgress = { if (first == null) first = it },
        )
        assertEquals(Anchor(30, 0), first!!.pages[0].anchor)
    }

    @Test
    fun `PAG-09 lazy means the provisional window measures only blocks near the anchor`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(400))
        val measurer = CountingMeasurer()
        var delivered = false
        val flow =
            Paginator(doc, measurer, testGeometry()).paginateAround(
                Anchor(300, 0),
                cancel = { delivered },
                onProgress = { delivered = true },
            )
        assertNull(flow)
        assertTrue(measurer.textCalls < 40, "measured ${measurer.textCalls} blocks before the first spread was shown")
    }

    @Test
    fun `PAG-09 cancellation stops the work and returns null`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(200))
        val paginator = Paginator(doc, FakeBlockMeasurer(), testGeometry())
        assertNull(paginator.paginateAll { true })
        var polls = 0
        val delivered = ArrayList<PageFlow>()
        val result =
            paginator.paginateAround(
                Anchor(0, 0),
                cancel = { ++polls > 5 },
                onProgress = { delivered.add(it) },
            )
        assertNull(result)
        assertTrue(delivered.none { it.complete })
    }

    @Test
    fun `PAG-09 the page count estimate is approximate while running and exact at the end`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(100))
        val seen = ArrayList<PageFlow>()
        val result =
            Paginator(doc, FakeBlockMeasurer(), testGeometry()).paginateAround(Anchor(0, 0), onProgress = {
                seen.add(it)
            })!!
        for (flow in seen) assertTrue(flow.estimatedPageCount >= flow.pages.size)
        assertEquals(result.pages.size, result.estimatedPageCount)
        val partial = seen.first { !it.provisional && !it.complete }
        val ratio = partial.estimatedPageCount.toDouble() / result.pages.size
        assertTrue(ratio in 0.5..2.0, "estimate ${partial.estimatedPageCount} vs real ${result.pages.size}")
    }

    @Test
    fun `PAG-10 returning to a previous geometry reuses the cached pagination`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(30))
        val measurer = CountingMeasurer()
        val service = PaginationService(LayoutCache(4))
        val folded = testGeometry(columns = 1)
        val unfolded = testGeometry(columns = 2)
        val a = service.paginate("hash-1", doc, measurer, folded, Anchor(0, 0))!!
        val b = service.paginate("hash-1", doc, measurer, unfolded, Anchor(0, 0))!!
        val calls = measurer.textCalls
        val again = service.paginate("hash-1", doc, measurer, folded, Anchor(10, 0))!!
        assertSame(a, again)
        assertNotSame(a, b)
        assertEquals(calls, measurer.textCalls, "a cache hit must not measure")
        assertNotSame(a, service.paginate("hash-2", doc, measurer, folded, Anchor(0, 0)))
        assertNotSame(a, service.paginate("hash-1", doc, measurer, testGeometry(typographyKey = 9), Anchor(0, 0)))
    }

    @Test
    fun `PAG-10 the cache evicts the least recently used entry and only stores complete flows`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(5))
        val cache = LayoutCache(2)
        val flows =
            (1..3).map {
                LayoutKey("h", testGeometry(lines = 10 + it)) to
                    Paginator(doc, FakeBlockMeasurer(), testGeometry()).paginateAll()!!
            }
        cache.put(flows[0].first, flows[0].second)
        cache.put(flows[1].first, flows[1].second)
        assertNotNull(cache[flows[0].first])
        cache.put(flows[2].first, flows[2].second)
        assertNull(cache[flows[1].first], "least recently used entry is evicted")
        assertEquals(2, cache.size)
        val provisional = PageFlow(emptyList(), 1, provisional = true, complete = false, consumed = 0f)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            cache.put(flows[0].first, provisional)
        }
    }

    @Test
    fun `ANC-03 the same anchor is on the visible spread after a geometry change`() {
        val doc =
            dev.dcesares.bifolio.parse.MarkdownParser
                .parse(manyParagraphs(60))
        val narrow = Paginator(doc, FakeBlockMeasurer(), testGeometry(lines = 8, columns = 1)).paginateAll()!!
        val wide = Paginator(doc, FakeBlockMeasurer(), testGeometry(lines = 14, columns = 2)).paginateAll()!!
        val anchor = narrow.pages[narrow.pageIndexOf(Anchor(33, 20))].anchor
        val page = wide.pages[wide.pageIndexOf(anchor)]
        val slice = page.slices.first { it.blockIndex == anchor.blockIndex }
        assertTrue(slice.blockIndex == anchor.blockIndex)
        assertTrue(wide.spreadIndexOf(anchor) in 0 until wide.spreadCount)
    }
}
