package dev.dcesares.bifolio.feature.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.Layout
import androidx.compose.ui.graphics.toArgb
import dev.dcesares.bifolio.designsystem.ReadingPalette
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.searchHit
import dev.dcesares.bifolio.designsystem.searchHitFocused
import dev.dcesares.bifolio.layout.SearchHighlighter
import dev.dcesares.bifolio.layout.TextLeaf

/**
 * What a paged frame needs to paint search hits (UI-08): the [highlighter] that finds them in the drawn text and the
 * paints, from the theme palette. Hits are painted under the glyphs, at draw time, so a page turn, a new current
 * result or closing search never rebuilds a prepared column.
 */
internal class PageHighlights(
    val highlighter: SearchHighlighter,
    palette: ReadingPalette,
    density: Float,
) {
    val hit: Paint = fill(palette.searchHit.toArgb())
    val focused: Paint = fill(palette.searchHitFocused.toArgb())

    /** The current result is also outlined, so it never relies on color alone. */
    val focusedOutline: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = Spacing.Hairline.value * density
        }

    private fun fill(color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
}

/** A paint op that can paint search hits. Ops without hits (rules, markers) stay plain [PaintOp]s. */
internal interface HighlightingOp : PaintOp {
    fun draw(
        canvas: Canvas,
        tableScroll: (Int) -> Int,
        highlights: PageHighlights?,
    )

    override fun draw(
        canvas: Canvas,
        tableScroll: (Int) -> Int,
    ) = draw(canvas, tableScroll, null)
}

/**
 * The hit shapes of one text leaf for one highlighter, found lazily and kept until the highlighter changes. Only
 * the leaves on screen are ever asked, and only on the main thread while drawing.
 */
internal class LeafHits(
    private val layout: Layout,
    private val leaf: TextLeaf,
) {
    private var forHighlighter: SearchHighlighter? = null
    private val hits = Path()
    private val current = Path()
    private val scratch = Path()

    /** Paints the hits under the text; the canvas is already translated to the leaf's origin. */
    fun paint(
        canvas: Canvas,
        highlights: PageHighlights,
    ) {
        refresh(highlights.highlighter)
        if (!hits.isEmpty) canvas.drawPath(hits, highlights.hit)
        if (!current.isEmpty) {
            canvas.drawPath(current, highlights.focused)
            canvas.drawPath(current, highlights.focusedOutline)
        }
    }

    private fun refresh(highlighter: SearchHighlighter) {
        if (forHighlighter === highlighter) return
        forHighlighter = highlighter
        hits.rewind()
        current.rewind()
        val length = layout.text.length
        for (range in highlighter.hitsIn(leaf.text, leaf.sourceStart, leaf.sourceEnd)) {
            val start = range.start.coerceIn(0, length)
            val end = range.end.coerceIn(start, length)
            if (end == start) continue
            scratch.rewind()
            layout.getSelectionPath(start, end, scratch)
            (if (range.focused) current else hits).addPath(scratch)
        }
    }
}
