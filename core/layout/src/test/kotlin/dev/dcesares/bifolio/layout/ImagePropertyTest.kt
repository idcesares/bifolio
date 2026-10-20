package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.parse.ImageSource
import dev.dcesares.bifolio.parse.ImageSources
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * PAG-07 and SYN-08 as properties: with images of every size among random blocks the coverage oracle
 * (PAG-02, PAG-03, PAG-06 and "every character once") still holds, every drawn image box fits its column and
 * 85 percent of the page, an image that starts a page did not fit the page before it, and the anchor survives a
 * change of geometry. Fixed seeds; every message carries the case.
 */
class ImagePropertyTest {
    /** Markdown of [count] blocks, about a fifth of them images named `img<k>.png`. */
    private fun markdownWithImages(
        seed: Long,
        count: Int,
    ): String {
        val random = Random(seed)
        val text = RandomMarkdown(seed)
        val parts = ArrayList<String>()
        repeat(count) { k ->
            val isImage = random.nextInt(IMAGE_ONE_IN) == 0
            parts.add(if (isImage) "![image $k](img$k.png)" else text.block())
        }
        return parts.joinToString("\n\n") + "\n"
    }

    /** A fixed, immutable answer per image: unknown, tiny, wide, tall, huge or ordinary. */
    private fun providerFor(seed: Long): ImageSizeProvider =
        ImageSizeProvider { block ->
            val k = block.source.filter { it.isDigit() }.toIntOrNull() ?: 0
            val r = Random(seed * 31 + k)
            when (r.nextInt(6)) {
                0 -> ImageSize.Unknown
                1 -> ImageSize.Known(r.nextInt(1, 60), r.nextInt(1, 60))
                2 -> ImageSize.Known(r.nextInt(1000, 6000), r.nextInt(50, 400))
                3 -> ImageSize.Known(r.nextInt(50, 400), r.nextInt(1000, 9000))
                4 -> ImageSize.Known(r.nextInt(2000, 8000), r.nextInt(2000, 8000))
                else -> ImageSize.Known(r.nextInt(100, 700), r.nextInt(100, 700))
            }
        }

    private fun checkRound(
        doc: Document,
        provider: ImageSizeProvider,
        geometry: Geometry,
        measurer: FakeBlockMeasurer,
        tag: String,
    ) {
        val flow = Paginator(doc, measurer, geometry, provider).paginateAll()!!
        PaginationOracle(doc, measurer, geometry, tag, provider).check(flow)
        val detailer = PageDetailer(doc, measurer, geometry, provider)
        val cap = maxOf(geometry.columnHeight * geometry.metrics.atomicMaxHeightPercent / PERCENT, 1)
        val equal = geometry.columnWidths.distinct().size == 1
        for ((p, page) in flow.pages.withIndex()) {
            val column = p % geometry.columnCount
            val width = geometry.columnWidths[column]
            val draw = detailer.detail(page, column)
            val atoms = draw.items.filterIsInstance<DrawAtomic>()
            for (a in atoms) {
                val inColumn = a.width in 1..width && a.x >= 0 && a.x + a.width <= width
                assertTrue(inColumn, "$tag page $p box ${a.x}+${a.width}/$width")
                assertTrue(a.height in 1..cap, "$tag page $p box height ${a.height} over cap $cap")
                assertTrue(a.y >= 0 && a.y + a.height <= draw.usedHeight, "$tag page $p box y=${a.y} h=${a.height}")
            }
            val first = page.slices.first()
            val startsImage = first.startLine == 0 && doc.blocks[first.blockIndex] is ImageBlock
            if (equal && p > 0 && startsImage) {
                val needed = flow.pages[p - 1].usedHeight + geometry.metrics.blockGap + atoms.first().height
                assertTrue(needed > geometry.columnHeight, "$tag page $p: the image would have fitted ($needed px)")
            }
        }
    }

    private fun anchorsSurvive(
        doc: Document,
        provider: ImageSizeProvider,
        a: Geometry,
        b: Geometry,
        measurer: FakeBlockMeasurer,
        random: Random,
        tag: String,
    ) {
        val flowA = Paginator(doc, measurer, a, provider).paginateAll()!!
        repeat(ANCHORS) {
            val anchor = flowA.pages[random.nextInt(flowA.pages.size)].anchor
            val seen = ArrayList<PageFlow>()
            Paginator(doc, measurer, b, provider).paginateAround(anchor, onProgress = { seen.add(it) })
            for (flow in seen) {
                val page = flow.pages[flow.pageIndexOf(anchor)]
                assertTrue(page.slices.any { it.blockIndex == anchor.blockIndex }, "$tag $anchor not on its page")
            }
        }
    }

    @Test
    fun `random documents with images of every size keep the oracle, the boxes and the anchors`() {
        val random = Random(SEED)
        var imageBlocks = 0
        repeat(DOCUMENTS) { n ->
            val seed = SEED + n
            val doc = MarkdownParser.parse(markdownWithImages(seed, BLOCKS))
            imageBlocks += doc.blocks.count { it is ImageBlock }
            val provider = providerFor(seed)
            val measurer = FakeBlockMeasurer(charWidth = random.nextInt(6, 13), lineHeight = 20)
            repeat(ROUNDS) { r ->
                val g = if (r % 2 == 0) RandomGeometries.equal(random) else RandomGeometries.mixed(random)
                val tag = "seed=$seed round=$r cols=${g.columnWidths} lines=${g.columnHeight / 20}"
                checkRound(doc, provider, g, measurer, tag)
                anchorsSurvive(doc, provider, g, RandomGeometries.any(random), measurer, random, tag)
            }
        }
        assertTrue(imageBlocks > DOCUMENTS, "the property would be vacuous: $imageBlocks images")
    }

    // Corpus C-17.

    private fun pngSize(bytes: ByteArray): ImageSize {
        fun u32(at: Int) = bytes.slice(at until at + 4).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        return ImageSize.Known(u32(PNG_WIDTH_AT), u32(PNG_HEIGHT_AT))
    }

    private val ihdrProvider =
        ImageSizeProvider { block ->
            val data = ImageSources.classify(block.source) as? ImageSource.DataUri
            if (data == null) ImageSize.Unknown else pngSize(data.decode())
        }

    @Test
    fun `C-17 the oversized corpus scales every picture, never enlarges the icon and satisfies the oracle`() {
        val doc = CorpusFiles.load("images-oversized.md")
        val sizes = doc.blocks.filterIsInstance<ImageBlock>().map { ihdrProvider.sizeOf(it) as ImageSize.Known }
        assertEquals(listOf(4000 to 300, 300 to 6000, 5000 to 5000, 32 to 32), sizes.map { it.width to it.height })
        val geometry = testGeometry()
        val measurer = FakeBlockMeasurer()
        val flow = Paginator(doc, measurer, geometry, ihdrProvider).paginateAll()!!
        PaginationOracle(doc, measurer, geometry, "C-17", ihdrProvider).check(flow)
        val detailer = PageDetailer(doc, measurer, geometry, ihdrProvider)
        val boxes = flow.pages.flatMap { detailer.detail(it, 0).items.filterIsInstance<DrawAtomic>() }
        assertEquals(listOf(200 to 15, 9 to 170, 170 to 170, 32 to 32), boxes.map { it.width to it.height })
        val imageSlices = flow.pages.flatMap { it.slices }.filter { doc.blocks[it.blockIndex] is ImageBlock }
        assertEquals(4, imageSlices.size)
        assertTrue(imageSlices.none { it.continuesOnNextPage || it.startLine > 0 })
    }

    @Test
    fun `C-17 the oversized corpus keeps the oracle at random geometries`() {
        val doc = CorpusFiles.load("images-oversized.md")
        val random = Random(SEED)
        repeat(CORPUS_ROUNDS) { r ->
            val g = RandomGeometries.any(random)
            val measurer = FakeBlockMeasurer(charWidth = random.nextInt(6, 13), lineHeight = 20)
            checkRound(doc, ihdrProvider, g, measurer, "C-17 round $r cols=${g.columnWidths}")
        }
    }

    private companion object {
        const val SEED = 20260820L
        const val DOCUMENTS = 40
        const val ROUNDS = 6
        const val BLOCKS = 45
        const val ANCHORS = 3
        const val CORPUS_ROUNDS = 60
        const val IMAGE_ONE_IN = 5
        const val PERCENT = 100
        const val PNG_WIDTH_AT = 16
        const val PNG_HEIGHT_AT = 20
    }
}
