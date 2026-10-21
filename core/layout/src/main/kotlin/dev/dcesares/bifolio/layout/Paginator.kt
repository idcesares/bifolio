package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document

/**
 * Paginates one [Document] at one [Geometry] with one [BlockMeasurer] (PAG-01 to PAG-12). Pure and
 * synchronous: callers run it on a background dispatcher and stop it through a [CancellationSignal].
 * Instances hold a measurement cache; use one per (document, geometry) and drop it afterwards.
 */
public class Paginator(
    private val document: Document,
    measurer: BlockMeasurer,
    private val geometry: Geometry,
    images: ImageSizeProvider = ImageSizeProvider.UNKNOWN,
    math: MathSizeProvider = MathSizeProvider.UNKNOWN,
) {
    private val flattener = BlockFlattener(document, measurer, geometry, images, math)
    private val columns = geometry.columnCount

    /** The whole document from its first character. Null when [cancel] fired. */
    public fun paginateAll(cancel: CancellationSignal = CancellationSignal.NEVER): PageFlow? {
        val composer = PageComposer(flattener, geometry, 0, 0, 0)
        val pages = ArrayList<Page>()
        while (true) {
            if (cancel.isCancelled()) return null
            pages.add(composer.next() ?: break)
        }
        return snapshot(pages, composer, provisional = false, complete = true)
    }

    /**
     * Anchored and lazy pagination (PAG-09). Calls [onProgress] with, in order:
     * 1. a provisional flow that starts at [anchor] and holds its spread plus two spreads after it;
     * 2. the canonical flow from the document start once it covers the anchor spread and two spreads after it;
     * 3. the complete flow, which is also the result.
     * The anchor is on the first spread of (1) and on the spread [PageFlow.spreadIndexOf] reports for (2) and (3).
     * Returns null when [cancel] fired; snapshots already delivered stay valid.
     */
    public fun paginateAround(
        anchor: Anchor,
        cancel: CancellationSignal = CancellationSignal.NEVER,
        onProgress: (PageFlow) -> Unit = {},
    ): PageFlow? {
        if (document.blocks.isEmpty()) return snapshot(emptyList(), null, provisional = false, complete = true)
        if (!provisional(anchor, cancel, onProgress)) return null
        val composer = PageComposer(flattener, geometry, 0, 0, 0)
        val pages = ArrayList<Page>()
        var announced = false
        while (true) {
            if (cancel.isCancelled()) return null
            pages.add(composer.next() ?: break)
            if (!announced && coversWindow(pages, anchor)) {
                announced = true
                onProgress(snapshot(ArrayList(pages), composer, provisional = false, complete = false))
            }
        }
        val done = snapshot(pages, composer, provisional = false, complete = true)
        onProgress(done)
        return done
    }

    private fun provisional(
        anchor: Anchor,
        cancel: CancellationSignal,
        onProgress: (PageFlow) -> Unit,
    ): Boolean {
        val start = locate(anchor)
        val composer = PageComposer(flattener, geometry, start.first, start.second, 0)
        val pages = ArrayList<Page>()
        while (pages.size < WINDOW_SPREADS * columns) {
            if (cancel.isCancelled()) return false
            pages.add(composer.next() ?: break)
        }
        onProgress(snapshot(pages, composer, provisional = true, complete = false))
        return true
    }

    private fun coversWindow(
        pages: List<Page>,
        anchor: Anchor,
    ): Boolean {
        if (pages.last().anchor.compareReadingOrder(anchor) <= 0) return false
        var i = pages.size - 1
        while (i > 0 && pages[i - 1].anchor.compareReadingOrder(anchor) > 0) i--
        val anchorPage = i - 1
        val needed = (anchorPage / columns + WINDOW_SPREADS) * columns
        return pages.size >= needed
    }

    /** Block and line that hold [anchor]; an anchor past the document clamps to its last line (ANC-05). */
    private fun locate(anchor: Anchor): Pair<Int, Int> {
        val blockIndex = anchor.blockIndex.coerceAtMost(document.blocks.size - 1)
        // Line numbers above 0 are those of the layout a block crossing columns uses (see PageComposer.next).
        val width = if (geometry.hasUnequalColumns) geometry.narrowestColumn else geometry.columnWidths[0]
        val layout = flattener.layout(blockIndex, width)
        if (layout.lineCount == 0) return blockIndex to 0
        val line =
            if (anchor.blockIndex >=
                document.blocks.size
            ) {
                layout.lineCount - 1
            } else {
                layout.lineAtOffset(anchor.charOffset)
            }
        return blockIndex to line
    }

    private fun snapshot(
        pages: List<Page>,
        composer: PageComposer?,
        provisional: Boolean,
        complete: Boolean,
    ): PageFlow {
        val length = document.source.length
        val consumed =
            when {
                complete -> 1f
                composer == null || length == 0 -> 0f
                else ->
                    document.blocks[composer.cursorBlock.coerceAtMost(document.blocks.size - 1)]
                        .range.start
                        .toFloat() /
                        length
            }
        return PageFlow(pages, columns, provisional, complete, consumed)
    }

    private companion object {
        /** The anchor spread plus two after it (PAG-09). */
        const val WINDOW_SPREADS = 3
    }
}
