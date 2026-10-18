package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.ThematicBreak
import io.kotest.property.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Property tests for PAG-02, PAG-03, PAG-06, PAG-11, PAG-12, SYN-15 and ANC-03 over the corpus and seeded random
 * documents. Failure messages carry the seed so any case can be replayed.
 */
class PaginationPropertyTest {
    private fun randomGeometry(random: Random): Geometry {
        val metrics =
            TEST_METRICS.copy(
                blockGap = random.nextInt(0, 31),
                innerGap = random.nextInt(0, 9),
            )
        return testGeometry(
            lines = random.nextInt(MIN_LINES, MAX_LINES + 1),
            columns = random.nextInt(1, 4),
            columnWidth = random.nextInt(MIN_WIDTH, MAX_WIDTH + 1),
            metrics = metrics,
        )
    }

    private fun measurerFor(random: Random) = FakeBlockMeasurer(charWidth = random.nextInt(6, 13), lineHeight = 20)

    private fun checkDocument(
        doc: Document,
        random: Random,
        label: String,
        rounds: Int,
        geometryOf: (Random) -> Geometry = ::randomGeometry,
        anchors: Boolean = true,
    ): Pair<Int, Int> {
        var checked = 0
        var skipped = 0
        repeat(rounds) { round ->
            val geometry = geometryOf(random)
            val measurer = measurerFor(random)
            val shape = "lines=${geometry.columnHeight / 20} cols=${geometry.columnCount} w=${geometry.columnWidths}"
            val tag = "$label seed=$SEED round=$round $shape"
            val flow = Paginator(doc, measurer, geometry).paginateAll()!!
            val oracle = PaginationOracle(doc, measurer, geometry, tag)
            oracle.check(flow)
            checkRules(doc, flow, tag)
            if (anchors) checkAnchors(doc, measurer, geometry, flow, random, tag)
            checked += oracle.headingsChecked
            skipped += oracle.headingsSkipped
        }
        return checked to skipped
    }

    /** SYN-15 and PAG-12 on the final flow. */
    private fun checkRules(
        doc: Document,
        flow: PageFlow,
        tag: String,
    ) {
        for (slice in flow.pages.flatMap { it.slices }) {
            if (doc.blocks[slice.blockIndex] is ThematicBreak) {
                assertEquals(0, slice.startLine, "$tag rule is never continued")
                assertTrue(!slice.continuesOnNextPage, "$tag rule is never split")
            }
        }
        assertEquals(flow.pages.size, flow.pages.count { it.slices.isNotEmpty() })
        assertEquals((flow.pages.size + flow.columnCount - 1) / flow.columnCount, flow.spreadCount)
    }

    /** ANC-03: an anchor taken from any other geometry stays on the page returned for it, provisional or final. */
    private fun checkAnchors(
        doc: Document,
        measurer: BlockMeasurer,
        geometry: Geometry,
        flow: PageFlow,
        random: Random,
        tag: String,
    ) {
        if (doc.blocks.isEmpty()) return
        val flattener = BlockFlattener(doc, measurer, geometry)
        val width = geometry.columnWidths[0]
        repeat(ANCHORS_PER_ROUND) {
            val block = random.nextInt(doc.blocks.size)
            val layout = flattener.layout(block, width)
            if (layout.lineCount == 0) return@repeat
            val line = random.nextInt(layout.lineCount)
            val span = maxOf(1, layout.srcEnd[line] - layout.srcStart[line])
            val inside = layout.srcStart[line] + random.nextInt(0, span)
            val anchor = Anchor(block, if (line == 0 && random.nextBoolean()) 0 else inside)
            val expected = layout.lineAtOffset(anchor.charOffset)

            assertHolds(flow, flattener, width, anchor, expected, "$tag final")
            val seen = ArrayList<PageFlow>()
            Paginator(doc, measurer, geometry).paginateAround(anchor, onProgress = { seen.add(it) })
            assertTrue(seen.isNotEmpty(), "$tag no progress for $anchor")
            assertEquals(0, seen.first().pageIndexOf(anchor), "$tag provisional: $anchor is on the first page")
            for ((i, f) in seen.withIndex()) assertHolds(f, flattener, width, anchor, expected, "$tag snapshot $i")
        }
    }

    private fun assertHolds(
        flow: PageFlow,
        flattener: BlockFlattener,
        width: Int,
        anchor: Anchor,
        expectedLine: Int,
        tag: String,
    ) {
        val page = flow.pages[flow.pageIndexOf(anchor)]
        val slice =
            page.slices.firstOrNull {
                it.blockIndex == anchor.blockIndex &&
                    expectedLine in it.startLine until it.endLine
            }
        assertTrue(slice != null, "$tag: $anchor (line $expectedLine) is not on page ${page.shape()}")
        assertTrue(flow.spreadIndexOf(anchor) == flow.pageIndexOf(anchor) / flow.columnCount)
        assertTrue(flattener.layout(anchor.blockIndex, width).lineCount > 0)
    }

    @Test
    fun `every corpus document keeps every character once and obeys PAG-02, PAG-03 and PAG-06 at random geometries`() {
        val random = Random(SEED)
        var checked = 0
        var skipped = 0
        for (name in CorpusFiles.names()) {
            val (c, s) = checkDocument(CorpusFiles.load(name), random, name, CORPUS_ROUNDS)
            checked += c
            skipped += s
        }
        println("corpus: PAG-03 checked $checked headings, skipped $skipped infeasible")
        assertTrue(checked > 0, "no heading was checked, the property would be vacuous")
    }

    @Test
    fun `random documents keep every character once and obey the pagination rules`() {
        val random = Random(SEED + 1)
        val source = RandomMarkdown(SEED)
        var checked = 0
        var skipped = 0
        repeat(RANDOM_DOCUMENTS) { n ->
            val (c, s) = checkDocument(source.document(RANDOM_BLOCKS), random, "random#$n", RANDOM_ROUNDS)
            checked += c
            skipped += s
        }
        println("random: PAG-03 checked $checked headings, skipped $skipped infeasible")
        assertTrue(checked > 0, "no heading was checked, the property would be vacuous")
    }

    @Test
    fun `LAY-12 columns of unequal width keep every character once and obey the pagination rules`() {
        val random = Random(SEED + 3)
        val source = RandomMarkdown(SEED + 3)
        var unequal = 0
        repeat(RANDOM_DOCUMENTS) { n ->
            val seen = ArrayList<Geometry>()
            val generate = { r: Random -> RandomGeometries.mixed(r).also { seen.add(it) } }
            checkDocument(source.document(RANDOM_BLOCKS), random, "mixed#$n", RANDOM_ROUNDS, generate, anchors = false)
            unequal += seen.count { it.columnWidths.distinct().size > 1 }
        }
        for (name in CorpusFiles.names()) {
            checkDocument(CorpusFiles.load(name), random, name, CORPUS_ROUNDS, RandomGeometries::mixed, anchors = false)
        }
        assertTrue(unequal > RANDOM_DOCUMENTS, "too few unequal geometries ($unequal), the property would be vacuous")
    }

    @Test
    fun `C-02 sixty thousand words of prose paginate completely and keep every character`() {
        val random = Random(SEED + 2)
        val doc = RandomMarkdown(SEED).prose(PROSE_WORDS)
        checkDocument(doc, random, "prose", 2)
    }

    @Test
    fun `a seed replays the same pagination`() {
        val doc = RandomMarkdown(SEED).document(RANDOM_BLOCKS)
        val geometry = randomGeometry(Random(SEED))
        val a = Paginator(doc, FakeBlockMeasurer(), geometry).paginateAll()!!
        val b = Paginator(doc, FakeBlockMeasurer(), geometry).paginateAll()!!
        assertEquals(a.pages, b.pages)
        assertEquals(RandomSource.seeded(SEED).random.nextInt(), RandomSource.seeded(SEED).random.nextInt())
    }

    private companion object {
        const val SEED = 20261017L
        const val MIN_LINES = 12
        const val MAX_LINES = 30
        const val MIN_WIDTH = 160
        const val MAX_WIDTH = 400
        const val CORPUS_ROUNDS = 12
        const val RANDOM_DOCUMENTS = 40
        const val RANDOM_BLOCKS = 60
        const val RANDOM_ROUNDS = 4
        const val ANCHORS_PER_ROUND = 6
        const val PROSE_WORDS = 60_000
    }
}
