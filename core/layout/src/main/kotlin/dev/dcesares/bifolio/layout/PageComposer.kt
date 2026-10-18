package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor

/**
 * Fills pages one column at a time from a cursor, applying PAG-02 to PAG-08 and SYN-15. Forward only,
 * greedy, and always progresses: a page that cannot satisfy a rule because the content does not fit even an
 * empty page uses the documented fallback (see [next]) instead of looping.
 *
 * Not thread-safe; one composer per run.
 */
internal class PageComposer(
    private val flattener: BlockFlattener,
    private val geometry: Geometry,
    startBlock: Int,
    startLine: Int,
    private var pageIndex: Int,
) {
    private var block = startBlock
    private var line = startLine

    /** Index of the block the cursor is in. */
    val cursorBlock: Int get() = block

    /** Number of pages produced so far by this composer. */
    var produced = 0
        private set

    private val columnCount = geometry.columnCount
    private val pageHeight = geometry.columnHeight
    private val blockGap = geometry.metrics.blockGap

    fun hasMore(): Boolean {
        skipEmptyBlocks()
        return block < flattener.blockCount
    }

    private fun widthOf(index: Int): Int = geometry.columnWidths[index % columnCount]

    private val narrowest = geometry.narrowestColumn
    private val mixed = geometry.hasUnequalColumns

    private fun skipEmptyBlocks() {
        val width = widthOf(pageIndex)
        // A line index above 0 belongs to the layout a block crossing columns is broken at (see next).
        while (block < flattener.blockCount &&
            line >= flattener.layout(block, if (mixed && line > 0) narrowest else width).lineCount
        ) {
            block++
            line = 0
        }
    }

    /**
     * Composes the next page, or null at the end of the document.
     *
     * Fallbacks when the content cannot meet a rule on an empty page, defined here so that pagination
     * always terminates: a heading plus its required lines taller than a page is placed without keep-with-next;
     * a unit taller than a page splits at the last line that fits, ignoring widow and orphan limits; a line
     * taller than a page is placed alone and overflows.
     *
     * Columns of unequal width (LAY-12): a block that fits whole in its column is laid out at that column's
     * width. A block that must split is laid out once at the narrowest column and every slice of it uses that
     * layout, so a break always lands on a line boundary of the layout that follows (nothing lost, nothing
     * repeated, widows and orphans exact). [PageDetailer] applies the same rule through [Geometry.sliceWidth].
     */
    fun next(): Page? {
        if (!hasMore()) return null
        val width = widthOf(pageIndex)
        val slices = ArrayList<Slice>(INITIAL_SLICES)
        var used = 0
        var anchor: Anchor? = null
        var open = true
        while (open && hasMore()) {
            val s = line
            val layout = layoutFor(width, s, used)
            val end = endFor(layout, s, used, width)
            if (end <= s) {
                open = false
            } else {
                val gap = if (used > 0 && s == 0) blockGap else 0
                val header = if (s > 0) layout.headerHeight else 0
                slices.add(Slice(block, s, end, layout.height(s, end) + header, end < layout.lineCount, header))
                if (anchor == null) anchor = Anchor(block, if (s == 0) 0 else layout.srcStart[s])
                used += gap + header + layout.height(s, end)
                if (end < layout.lineCount) {
                    line = end
                    open = false
                } else {
                    block++
                    line = 0
                }
            }
        }
        val page = Page(slices, anchor!!, used, endsDocument = !hasMore())
        pageIndex++
        produced++
        return page
    }

    /** The layout the block at the cursor is placed with: see [next]. */
    private fun layoutFor(
        width: Int,
        s: Int,
        used: Int,
    ): BlockLayout {
        val own = flattener.layout(block, width)
        if (!mixed || width == narrowest) return own
        if (s > 0) return flattener.layout(block, narrowest)
        val gap = if (used > 0) blockGap else 0
        if (own.lineCount == 1 || own.height(0, own.lineCount) <= pageHeight - used - gap) return own
        return flattener.layout(block, narrowest)
    }

    /** End line (exclusive) of the slice that starts at [s] on a page already holding [used] px; [s] if none fits. */
    private fun endFor(
        layout: BlockLayout,
        s: Int,
        used: Int,
        width: Int,
    ): Int {
        val gap = if (used > 0 && s == 0) blockGap else 0
        val header = if (s > 0) layout.headerHeight else 0
        val room = pageHeight - used - gap - header
        val keepFails = used > 0 && s == 0 && layout.keepWithNext && gap + chainHeight(block, width) > pageHeight - used
        return when {
            keepFails -> s
            layout.height(s, layout.lineCount) <= room -> layout.lineCount
            else -> splitPoint(layout, s, room, pageEmpty = used == 0)
        }
    }

    /**
     * Largest allowed break in `(s, n)` whose lines fit in [room]; 0 when there is none and the page already
     * has content (the caller then flushes); on an empty page falls back to the largest fitting line count, at
     * least one line.
     */
    private fun splitPoint(
        layout: BlockLayout,
        s: Int,
        room: Int,
        pageEmpty: Boolean,
    ): Int {
        val fit = lastFitting(layout, s, room)
        var b = fit
        while (b > s && !layout.allowsBreak(s, b)) b--
        if (b > s) return b
        return if (pageEmpty) maxOf(fit, s + 1) else s
    }

    /** Largest `e` in `(s, n)` with `height(s, e) <= room`; `s` when not even one line fits. */
    private fun lastFitting(
        layout: BlockLayout,
        s: Int,
        room: Int,
    ): Int {
        var lo = s
        var hi = layout.lineCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (layout.height(s, mid) <= room) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * Height of the heading at [start] plus what PAG-03 requires after it: the first lines of the next block
     * (at least two, never splitting a unit that must stay whole), or the whole next block when it is atomic
     * or a heading. Stops accumulating once it exceeds a page.
     */
    private fun chainHeight(
        start: Int,
        width: Int,
    ): Int {
        var total = 0
        var i = start
        while (i < flattener.blockCount && total <= pageHeight) {
            val l = flattener.layout(i, width)
            if (l.lineCount == 0) {
                i++
                continue
            }
            val gap = if (i == start) 0 else blockGap
            if (!l.keepWithNext) {
                var lead = l.height(0, l.leadLines())
                if (mixed && width != narrowest) {
                    // The lines that follow a heading may cross columns, which breaks them at the narrowest.
                    val n = flattener.layout(i, narrowest)
                    lead = maxOf(lead, n.height(0, n.leadLines()))
                }
                return total + gap + lead
            }
            total += gap + l.height(0, l.lineCount)
            i++
        }
        return total
    }
}

private const val INITIAL_SLICES = 4
