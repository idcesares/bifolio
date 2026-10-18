package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * ANC-01, ANC-03, ANC-04 (anchor is mode independent) and ANC-05 over random documents and random pairs of
 * geometries, equal columns, unequal columns and fold geometries. Fixed seeds; every message carries the case.
 */
class SpreadAnchorTest {
    /** A document, a measurer and a geometry with the machinery to ask where an anchor is drawn. */
    private class Setup(
        val doc: Document,
        val measurer: BlockMeasurer,
        val geometry: Geometry,
    ) {
        private val flattener = BlockFlattener(doc, measurer, geometry)

        fun paginateAll(): PageFlow = Paginator(doc, measurer, geometry).paginateAll()!!

        fun snapshots(anchor: Anchor): List<PageFlow> {
            val seen = ArrayList<PageFlow>()
            Paginator(doc, measurer, geometry).paginateAround(anchor, onProgress = { seen.add(it) })
            return seen
        }

        /** Line of the block that holds [anchor] at the width the flow lays that block out at (first column). */
        fun lineSpan(anchor: Anchor): Int {
            val width = if (geometry.hasUnequalColumns) geometry.narrowestColumn else geometry.columnWidths[0]
            val layout = flattener.layout(anchor.blockIndex, width)
            val line = layout.lineAtOffset(anchor.charOffset)
            return layout.srcEnd[line] - layout.srcStart[line] + 1
        }

        /** True when a column of [spread] draws the line of [anchor]: the character is on screen. */
        fun shows(
            flow: PageFlow,
            spread: Int,
            anchor: Anchor,
        ): Boolean {
            for ((column, page) in flow.pagesOfSpread(spread).withIndex()) {
                for (slice in page.slices) {
                    if (slice.blockIndex != anchor.blockIndex) continue
                    val layout = flattener.layout(slice.blockIndex, geometry.sliceWidth(column, slice))
                    if (layout.lineAtOffset(anchor.charOffset) in slice.startLine until slice.endLine) return true
                }
            }
            return false
        }

        /** An anchor inside a random line of a random page of [flow], not necessarily at a line start. */
        fun anyAnchor(
            flow: PageFlow,
            random: Random,
        ): Anchor {
            val pageIndex = random.nextInt(flow.pages.size)
            val page = flow.pages[pageIndex]
            val slice = page.slices[random.nextInt(page.slices.size)]
            val layout = flattener.layout(slice.blockIndex, geometry.sliceWidth(pageIndex % flow.columnCount, slice))
            val line = slice.startLine + random.nextInt(slice.lineCount)
            val span = maxOf(1, layout.srcEnd[line] - layout.srcStart[line])
            return Anchor(slice.blockIndex, layout.srcStart[line] + random.nextInt(span))
        }
    }

    private fun cases(
        seed: Long,
        count: Int,
        body: (Setup, Setup, Random, String) -> Unit,
    ) {
        val random = Random(seed)
        val source = RandomMarkdown(seed)
        repeat(count) { n ->
            val doc = source.document(BLOCKS)
            val measurer = FakeBlockMeasurer(charWidth = random.nextInt(6, 13), lineHeight = 20)
            val a = Setup(doc, measurer, RandomGeometries.any(random))
            val b = Setup(doc, measurer, RandomGeometries.any(random))
            val tag =
                "seed=$seed doc#$n A=${a.geometry.columnWidths}x${a.geometry.columnHeight} " +
                    "B=${b.geometry.columnWidths}x${b.geometry.columnHeight}"
            body(a, b, random, tag)
        }
    }

    @Test
    fun `ANC-03 the anchor is visible on the spread found for it after any geometry change`() {
        cases(SEED, DOCUMENTS) { a, b, random, tag ->
            val flowA = a.paginateAll()
            repeat(ANCHORS) { k ->
                val settled = AnchorMapper.leftmostVisibleAnchor(flowA, random.nextInt(flowA.spreadCount))!!
                for (anchor in listOf(settled, a.anyAnchor(flowA, random))) {
                    val snapshots = b.snapshots(anchor)
                    // The canonical stage is skipped when fewer than two spreads follow the anchor (PAG-09).
                    assertTrue(snapshots.size in 2..3, "$tag anchor#$k $anchor: ${snapshots.size} snapshots")
                    assertTrue(snapshots.first().provisional && snapshots.last().complete, "$tag $anchor stages")
                    for ((i, flow) in snapshots.withIndex()) {
                        val spread = AnchorMapper.spreadContaining(flow, anchor)
                        assertTrue(spread >= 0, "$tag $anchor snapshot $i has no spread")
                        assertTrue(
                            b.shows(flow, spread, anchor),
                            "$tag $anchor not drawn on spread $spread, snapshot $i",
                        )
                        if (flow.provisional) assertEquals(0, spread, "$tag $anchor is on the first provisional spread")
                    }
                }
            }
        }
    }

    @Test
    fun `ANC-01 the settled anchor of a spread is its first page and is stable under the same geometry`() {
        cases(SEED + 1, DOCUMENTS) { a, _, random, tag ->
            val flow = a.paginateAll()
            for (spread in 0 until flow.spreadCount) {
                val anchor = AnchorMapper.leftmostVisibleAnchor(flow, spread)!!
                assertEquals(flow.pagesOfSpread(spread).first().anchor, anchor, "$tag spread $spread")
                assertEquals(spread, AnchorMapper.spreadContaining(flow, anchor), "$tag spread $spread")
                assertTrue(a.shows(flow, spread, anchor), "$tag spread $spread does not draw its own anchor")
            }
            assertEquals(null, AnchorMapper.leftmostVisibleAnchor(flow, -1), tag)
            assertEquals(null, AnchorMapper.leftmostVisibleAnchor(flow, flow.spreadCount), tag)
            val any = a.anyAnchor(flow, random)
            val settled = AnchorMapper.leftmostVisibleAnchor(flow, AnchorMapper.spreadContaining(flow, any))!!
            assertTrue(settled.compareReadingOrder(any) <= 0, "$tag settling never moves forward past $any")
        }
    }

    @Test
    fun `ANC-03 a round trip A to B to A never moves forward, the provisional path stays within a line`() {
        var worstSpreads = 0
        cases(SEED + 2, DOCUMENTS) { a, b, random, tag ->
            val flowA = a.paginateAll()
            val flowB = b.paginateAll()
            repeat(ANCHORS) { k ->
                val spread = random.nextInt(flowA.spreadCount)
                val a0 = AnchorMapper.leftmostVisibleAnchor(flowA, spread)!!
                val note = "$tag anchor#$k $a0"

                // Complete flows: settle on B, settle back on A.
                val a1 = AnchorMapper.leftmostVisibleAnchor(flowB, AnchorMapper.spreadContaining(flowB, a0))!!
                val a2 = AnchorMapper.leftmostVisibleAnchor(flowA, AnchorMapper.spreadContaining(flowA, a1))!!
                assertTrue(a1.compareReadingOrder(a0) <= 0, "$note forward on B: $a1")
                assertTrue(a2.compareReadingOrder(a1) <= 0, "$note forward on return: $a2")
                assertTrue(a.shows(flowA, AnchorMapper.spreadContaining(flowA, a2), a2), "$note a2 not drawn")
                val drift = spread - AnchorMapper.spreadContaining(flowA, a2)
                worstSpreads = maxOf(worstSpreads, drift)

                // Provisional flows start at the anchor line: same block, same line, offset only moves back.
                val p1 =
                    b
                        .snapshots(a0)
                        .first()
                        .pages
                        .first()
                        .anchor
                val p2 =
                    a
                        .snapshots(p1)
                        .first()
                        .pages
                        .first()
                        .anchor
                assertEquals(a0.blockIndex, p1.blockIndex, "$note provisional B left the block")
                assertEquals(a0.blockIndex, p2.blockIndex, "$note provisional A left the block")
                assertTrue(p1.charOffset <= a0.charOffset && p2.charOffset <= p1.charOffset, "$note forward: $p1 $p2")
                assertTrue(
                    a0.charOffset - p2.charOffset <= b.lineSpan(a0) + a.lineSpan(p1),
                    "$note moved more than a line on each side: $p1 $p2",
                )
            }
        }
        println("ANC-03 round trip: worst backward drift on complete flows was $worstSpreads spreads of A")
    }

    @Test
    fun `ANC-03 the same geometry round trip returns the very same anchor`() {
        cases(SEED + 3, DOCUMENTS) { a, _, random, tag ->
            val flow = a.paginateAll()
            repeat(ANCHORS) {
                val a0 = AnchorMapper.leftmostVisibleAnchor(flow, random.nextInt(flow.spreadCount))!!
                val again = a.snapshots(a0)
                for (f in again) {
                    val s = AnchorMapper.spreadContaining(f, a0)
                    if (!f.provisional) assertEquals(a0, AnchorMapper.leftmostVisibleAnchor(f, s), "$tag $a0")
                }
            }
        }
    }

    @Test
    fun `ANC-04 an anchor is a source position, so the scroll surface and the pages agree on it`() {
        cases(SEED + 4, DOCUMENTS) { a, b, random, tag ->
            val flowA = a.paginateAll()
            repeat(ANCHORS) {
                val a0 = AnchorMapper.leftmostVisibleAnchor(flowA, random.nextInt(flowA.spreadCount))!!
                // Scroll mode keeps the position as a source offset; coming back to pages must find the same anchor.
                assertEquals(a0, a.doc.anchorAt(a.doc.sourceOffsetOf(a0)), "$tag $a0")
                val flowB = b.paginateAll()
                val s = AnchorMapper.spreadContaining(flowB, a0)
                assertTrue(b.shows(flowB, s, a0), "$tag $a0 not drawn after the mode round trip")
            }
        }
    }

    @Test
    fun `ANC-05 an anchor past the end of the document is shown on the last spread under any geometry`() {
        cases(SEED + 5, DOCUMENTS) { a, _, random, tag ->
            val flow = a.paginateAll()
            val last = a.doc.blocks.size
            val stale =
                listOf(
                    Anchor(last + random.nextInt(0, 50), random.nextInt(0, 500)),
                    Anchor(last - 1, 100_000),
                    Anchor(random.nextInt(0, last), 100_000),
                )
            for (anchor in stale) {
                val flows = a.snapshots(anchor)
                assertTrue(flows.size in 2..3, "$tag $anchor: ${flows.size} snapshots")
                for (f in flows) {
                    val s = AnchorMapper.spreadContaining(f, anchor)
                    assertTrue(s in 0 until f.spreadCount, "$tag $anchor has no spread in ${f.pages.size} pages")
                    val drawn = if (anchor.blockIndex >= last) Anchor(last - 1, Int.MAX_VALUE) else anchor
                    assertTrue(a.shows(f, s, drawn), "$tag $anchor")
                }
                if (anchor.blockIndex >= last) {
                    assertEquals(flow.spreadCount - 1, AnchorMapper.spreadContaining(flow, anchor), "$tag $anchor")
                }
            }
        }
    }

    @Test
    fun `ANC-01 right to left documents anchor on the rightmost column, which is their first page`() {
        val (_, flow) = paginate(markdown(para(25), para(25, letter = 'b')), testGeometry(lines = 6, columns = 3))
        assertTrue(flow.pages.size >= 3)
        val first = flow.pagesOfSpread(0)
        // Pages follow reading order (PAG-12): left to right documents put page 0 in the leftmost column,
        // right to left documents mirror the columns and put it in the rightmost one.
        assertEquals(0, AnchorMapper.visualColumn(0, 3, rtl = false))
        assertEquals(2, AnchorMapper.visualColumn(0, 3, rtl = true))
        assertEquals(first[0].anchor, AnchorMapper.leftmostVisibleAnchor(flow, 0, rtl = false))
        assertEquals(first[0].anchor, AnchorMapper.leftmostVisibleAnchor(flow, 0, rtl = true))
        val rightmost = first.indices.first { AnchorMapper.visualColumn(it, 3, rtl = true) == 2 }
        assertEquals(first[0].anchor, first[rightmost].anchor)
        val leftmost = first.indices.first { AnchorMapper.visualColumn(it, 3, rtl = true) == 0 }
        assertTrue(first[leftmost].anchor.compareReadingOrder(first[0].anchor) > 0, "leftmost of RTL is read last")
        assertNotNull(AnchorMapper.leftmostVisibleAnchor(flow, flow.spreadCount - 1, rtl = true), "short last spread")
    }

    private companion object {
        const val SEED = 20261018L
        const val DOCUMENTS = 25
        const val BLOCKS = 50
        const val ANCHORS = 4
    }
}
