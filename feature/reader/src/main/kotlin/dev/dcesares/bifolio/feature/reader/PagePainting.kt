package dev.dcesares.bifolio.feature.reader

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.text.Layout
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.ReadingPalette
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.BarKind
import dev.dcesares.bifolio.layout.DrawAtomic
import dev.dcesares.bifolio.layout.DrawBar
import dev.dcesares.bifolio.layout.DrawCalloutTitle
import dev.dcesares.bifolio.layout.DrawCodeSurface
import dev.dcesares.bifolio.layout.DrawContinuation
import dev.dcesares.bifolio.layout.DrawMarker
import dev.dcesares.bifolio.layout.DrawRule
import dev.dcesares.bifolio.layout.DrawTableRow
import dev.dcesares.bifolio.layout.DrawText
import dev.dcesares.bifolio.layout.FrontMatterCard
import dev.dcesares.bifolio.layout.MarkerKind
import dev.dcesares.bifolio.layout.MathSize
import dev.dcesares.bifolio.layout.PageDraw
import dev.dcesares.bifolio.layout.TargetKind
import dev.dcesares.bifolio.layout.TextLeaf
import dev.dcesares.bifolio.layout.TextRole
import dev.dcesares.bifolio.layout.TextStyles
import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.ColumnAlignment
import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.text.ColorRuns
import dev.dcesares.bifolio.text.TextLayoutFactory
import dev.dcesares.bifolio.text.TextPalette

/** One thing to paint on a page column, ready to run on the main thread: no measuring, no allocation of layouts. */
internal fun interface PaintOp {
    /** [tableScroll] returns the horizontal scroll in px of the table with the given block index. */
    fun draw(
        canvas: Canvas,
        tableScroll: (Int) -> Int,
    )
}

/** Where a scrollable table sits in its column, for hit testing a drag (PAG-06). Pixels from the column origin. */
internal class TableHit(
    val blockIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maxScroll: Int,
)

/** A page column ready to draw. */
internal class PreparedColumn(
    val ops: List<PaintOp>,
    val tables: List<TableHit>,
    /** Links and footnote markers of the column, in px from the column origin (UI-09, NAV-07). */
    val targets: List<TargetBox> = emptyList(),
    /** The text of the column that can be selected (NAV-09), in reading order. Table cells are not part of it. */
    val texts: List<ColumnText> = emptyList(),
    /** The display formulas of the column (A11Y-04): TalkBack nodes are made from them. */
    val maths: List<MathBox> = emptyList(),
    /** The images of the column (A11Y-04): TalkBack nodes carry their alt text. */
    val images: List<ImageBox> = emptyList(),
    /** The table rows of the column (A11Y-01): TalkBack reads a row as one line of cells. */
    val rows: List<RowBox> = emptyList(),
)

/** One table row in a column, in px from the column origin, with its cells' text in order. */
internal class RowBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val spoken: String,
    val header: Boolean,
)

/** One image in a column, in px from the column origin. */
internal class ImageBox(
    val block: ImageBlock,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** One display formula in a column, in px from the column origin. */
internal class MathBox(
    val block: MathBlock,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * One text of a column that can be selected: its platform layout, where it sits (px from the column origin) and
 * which characters are on this page. Kept from the layout the page is painted with, so nothing is measured again.
 */
internal class ColumnText(
    val layout: Layout,
    /** Left edge of the text, and top of the whole leaf (above the first visible line when the leaf is split). */
    val x: Float,
    val y: Float,
    val width: Float,
    val clipTop: Float,
    val clipBottom: Float,
    val visibleStart: Int,
    val visibleEnd: Int,
    val hidden: List<IntRange>,
    /** What the text is, for TalkBack: headings are marked as such (A11Y-01). */
    val role: TextRole = TextRole.BODY,
    /** Info string of a fenced code block this text belongs to (A11Y-04). */
    val language: String? = null,
) {
    /** The characters TalkBack reads: the visible lines, without the footnote marker labels (they are nodes). */
    val spoken: String
        get() =
            buildString {
                val text = layout.text
                for (i in visibleStart until visibleEnd.coerceAtMost(text.length)) {
                    if (hidden.none { i in it }) append(text[i])
                }
            }.trim()

    val top: Float get() = y + clipTop
    val height: Float get() = clipBottom - clipTop
}

/** Callout labels resolved from string resources on the main thread (UI-13). */
internal data class CalloutLabels(
    private val labels: Map<CalloutKind, String>,
) {
    operator fun get(kind: CalloutKind): String = labels.getValue(kind)
}

private const val BULLET_RADIUS_EM = 0.12f
private const val MARKER_GAP_EM = 0.4f
private const val MARKER_MIDLINE_EM = 0.3f
private const val CHECK_SIZE_EM = 0.62f
private const val CONTINUATION_OFFSET_EM = 0.9f
private const val DOT_RADIUS_EM = 0.09f
private const val ORNAMENT_SPACING_EM = 0.6f
private const val ORNAMENT_DROP_EM = 2f
private const val ORNAMENT_DOTS = 3
private const val DOT_STEP = 3f
private const val MARK_EM = 0.9f
private const val MARK_GRID = 24f
private const val MARK_PADDINGS = 3

// Slots of the int array that tracks the extent of a scrollable table in a column.
private const val X = 0
private const val TOP = 1
private const val BOTTOM = 2
private const val SCROLL = 3
private const val WIDTH = 4
private const val STROKE_CHECK = 1.5f
private const val CHECK_P1_X = 0.2f
private const val CHECK_P1_Y = 0.5f
private const val CHECK_P2_X = 0.42f
private const val CHECK_P2_Y = 0.72f
private const val CHECK_P3_X = 0.8f
private const val CHECK_P3_Y = 0.28f

/**
 * Builds [PaintOp]s from the draw model with the same `StaticLayout` recipe the measurer used (ADR-0013).
 * Everything here may run on a background thread; the ops it returns are drawn on the main thread.
 */
@Suppress("TooManyFunctions") // One small function per kind of draw item.
internal class ColumnPainter(
    private val palette: ReadingPalette,
    private val density: Float,
    private val bodyPx: Float,
    private val cellPadding: Float,
    private val factory: TextLayoutFactory,
    private val labels: CalloutLabels,
    /** Where image pixels come from (SYN-08); null paints every image as a frame. */
    private val images: ImageRenderer? = null,
    /** Where display formulas come from (SYN-12); null paints every formula as its source. */
    private val math: MathRenderer? = null,
) {
    private val textPalette =
        TextPalette(
            ink = palette.ink.toArgb(),
            inkMuted = palette.inkMuted.toArgb(),
            link = palette.link.toArgb(),
            accent = palette.accent.toArgb(),
            mark = palette.accent.copy(alpha = MARK_ALPHA).toArgb(),
            codeSurface = palette.codeSurface.toArgb(),
        )
    private val leafLayouts =
        object : LinkedHashMap<TextLeaf, Layout>(LEAF_CACHE, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextLeaf, Layout>): Boolean =
                size > LEAF_CACHE
        }
    private val fill = paint(palette.codeSurface.toArgb(), Paint.Style.FILL)
    private val ruleFill = paint(palette.rule.toArgb(), Paint.Style.FILL)
    private val accentFill = paint(palette.accent.toArgb(), Paint.Style.FILL)
    private val mutedFill = paint(palette.inkMuted.toArgb(), Paint.Style.FILL)
    private val ruleStroke =
        paint(palette.rule.toArgb(), Paint.Style.STROKE).apply {
            strokeWidth =
                px(Spacing.Rule.value)
        }
    private val mutedStroke =
        paint(palette.inkMuted.toArgb(), Paint.Style.STROKE).apply {
            strokeWidth = px(STROKE_CHECK)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val titlePaint =
        factory.paintOf(TextRole.BODY, textPalette).apply {
            color = palette.accent.toArgb()
            isFakeBoldText = true
        }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val markStroke =
        paint(palette.inkMuted.toArgb(), Paint.Style.STROKE).apply {
            // In icon grid units: the canvas is scaled to the mark, so this is the icon's own 1.5 stroke.
            strokeWidth = STROKE_CHECK
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val markerPaint = factory.paintOf(TextRole.BODY, textPalette).apply { color = palette.inkMuted.toArgb() }

    private fun px(dp: Float): Float = dp * density

    private fun paint(
        color: Int,
        style: Paint.Style,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
        }

    @Synchronized
    private fun layoutOf(leaf: TextLeaf): Layout =
        leafLayouts.getOrPut(leaf) {
            factory.layout(leaf.text, leaf.role, leaf.width, leaf.styles, textPalette, codeRunsOf(leaf))
        }

    /** Syntax colors of a fenced code leaf (SYN-11); null leaves the text in `ink`. Runs off the main thread. */
    private fun codeRunsOf(leaf: TextLeaf): ColorRuns? {
        if (leaf.role != TextRole.CODE) return null
        val spans = CodeTokenCache.spansOf(leaf.text, leaf.language) ?: return null
        return colorRunsOf(spans, palette.codeTokens)
    }

    /** Ops for one column of [draw], in paint order, plus the end ornament when the page ends the document (PAG-12). */
    fun prepare(
        draw: PageDraw,
        columnHeight: Int,
    ): PreparedColumn {
        val ops = ArrayList<PaintOp>(draw.items.size + 1)
        val tableRects = LinkedHashMap<Int, IntArray>()
        val marks = ArrayList<PaintOp>(1)
        val targets = ArrayList<TargetBox>(0)
        val texts = ArrayList<ColumnText>()
        val maths = ArrayList<MathBox>(0)
        val pictures = ArrayList<ImageBox>(0)
        val rows = ArrayList<RowBox>(0)
        for (item in draw.items) {
            when (item) {
                is DrawText -> {
                    ops.add(textOp(item))
                    columnText(item)?.let(texts::add)
                    if (item.leaf.targets.isNotEmpty()) targetBoxes(item, targets)
                }
                is DrawMarker -> ops.add(markerOp(item))
                is DrawBar -> ops.add(barOp(item))
                is DrawCodeSurface -> ops.add(codeSurfaceOp(item))
                is DrawRule -> ops.add(ruleOp(item))
                is DrawAtomic -> {
                    ops.add(atomicOrMediaOp(item))
                    mathBoxOf(item)?.let(maths::add)
                    imageBoxOf(item)?.let(pictures::add)
                }
                is DrawCalloutTitle -> ops.add(titleOp(item))
                is DrawContinuation -> marks.add(continuationOp(item))
                is DrawTableRow -> {
                    ops.add(tableRowOp(item))
                    rows.add(rowBoxOf(item))
                    if (item.tableWidth > item.columnWidth) {
                        val r =
                            tableRects.getOrPut(item.blockIndex) {
                                intArrayOf(
                                    item.x,
                                    item.y,
                                    item.y + item.height,
                                    item.tableWidth - item.columnWidth,
                                    item.columnWidth,
                                )
                            }
                        r[TOP] = minOf(r[TOP], item.y)
                        r[BOTTOM] = maxOf(r[BOTTOM], item.y + item.height)
                    }
                }
            }
        }
        // Painted last: the dots sit on the code surface and must not be covered by it.
        ops.addAll(marks)
        if (draw.endsDocument) ops.add(ornamentOp(draw, columnHeight))
        val hits =
            tableRects.map { (block, r) ->
                TableHit(block, r[X], r[TOP], r[WIDTH], r[BOTTOM] - r[TOP], r[SCROLL])
            }
        return PreparedColumn(ops, hits, targets, texts, maths, pictures, rows)
    }

    private fun rowBoxOf(row: DrawTableRow): RowBox =
        RowBox(
            x = row.x.toFloat(),
            y = row.y.toFloat(),
            width = row.columnWidth.toFloat(),
            height = row.height.toFloat(),
            spoken =
                row.cells
                    .mapNotNull { it.leaf?.text?.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", "),
            header = row.header,
        )

    private fun imageBoxOf(item: DrawAtomic): ImageBox? =
        (item.block as? ImageBlock)?.let {
            ImageBox(it, item.x.toFloat(), item.y.toFloat(), item.width.toFloat(), item.height.toFloat())
        }

    private fun mathBoxOf(item: DrawAtomic): MathBox? =
        (item.block as? MathBlock)?.let {
            MathBox(it, item.x.toFloat(), item.y.toFloat(), item.width.toFloat(), item.height.toFloat())
        }

    /**
     * One box per line a link or marker of [item] covers, in px from the column origin and only where the text is
     * visible (a leaf split over two pages shows a slice of its lines).
     */
    private fun targetBoxes(
        item: DrawText,
        out: MutableList<TargetBox>,
    ) {
        val layout = layoutOf(item.leaf)
        val length = layout.text.length
        val dy = (item.y - item.clipTop).toFloat()
        for (target in item.leaf.targets) {
            val start = target.start.coerceIn(0, length)
            val end = target.end.coerceIn(start, length)
            if (end == start) continue
            for (line in layout.getLineForOffset(start)..layout.getLineForOffset(end - 1)) {
                val top = maxOf(layout.getLineTop(line), item.clipTop)
                val bottom = minOf(layout.getLineBottom(line), item.clipBottom)
                if (bottom <= top) continue
                val lineEnd = layout.getLineEnd(line)
                val a = layout.getPrimaryHorizontal(maxOf(start, layout.getLineStart(line)))
                val b = if (end >= lineEnd) layout.getLineRight(line) else layout.getPrimaryHorizontal(end)
                out.add(
                    TargetBox(
                        item.x + minOf(a, b),
                        dy + top,
                        item.x + maxOf(a, b),
                        dy + bottom,
                        target.kind,
                        target.value,
                    ),
                )
            }
        }
    }

    /** The selectable slice of [item]: the lines on this page, with the footnote marker labels marked as hidden. */
    private fun columnText(item: DrawText): ColumnText? {
        val layout = layoutOf(item.leaf)
        val length = layout.text.length
        if (length == 0 || layout.lineCount == 0) return null
        val first = layout.getLineForVertical(item.clipTop)
        val last = layout.getLineForVertical((item.clipBottom - 1).coerceAtLeast(item.clipTop))
        val from = layout.getLineStart(first)
        val to = layout.getLineEnd(last)
        if (to <= from) return null
        val hidden =
            item.leaf.targets
                .filter { it.kind == TargetKind.NOTE && it.end > it.start }
                .map { it.start.coerceIn(0, length) until it.end.coerceIn(0, length) }
        return ColumnText(
            layout = layout,
            x = item.x.toFloat(),
            y = (item.y - item.clipTop).toFloat(),
            width = item.leaf.width.toFloat(),
            clipTop = item.clipTop.toFloat(),
            clipBottom = item.clipBottom.toFloat(),
            visibleStart = from,
            visibleEnd = to,
            hidden = hidden,
            role = item.leaf.role,
            language = item.leaf.language,
        )
    }

    private fun textOp(item: DrawText): PaintOp {
        val layout = layoutOf(item.leaf)
        val x = item.x.toFloat()
        val y = (item.y - item.clipTop).toFloat()
        val width = item.leaf.width.toFloat()
        val top = item.clipTop.toFloat()
        val bottom = item.clipBottom.toFloat()
        val hits = LeafHits(layout, item.leaf)
        return object : HighlightingOp {
            override fun draw(
                canvas: Canvas,
                tableScroll: (Int) -> Int,
                highlights: PageHighlights?,
            ) {
                canvas.save()
                canvas.translate(x, y)
                canvas.clipRect(0f, top, width, bottom)
                if (highlights != null) hits.paint(canvas, highlights)
                layout.draw(canvas)
                canvas.restore()
            }
        }
    }

    private fun markerOp(item: DrawMarker): PaintOp {
        val metrics = markerPaint.fontMetrics
        val baseline = item.y - metrics.ascent
        val midline = baseline - bodyPx * MARKER_MIDLINE_EM
        val right = item.x + item.width - bodyPx * MARKER_GAP_EM
        return when (item.kind) {
            MarkerKind.BULLET -> {
                val r = bodyPx * BULLET_RADIUS_EM
                PaintOp { canvas, _ -> canvas.drawCircle(right - r, midline, r, mutedFill) }
            }
            MarkerKind.NUMBER -> {
                val text = "${item.number}."
                val x = right - markerPaint.measureText(text)
                PaintOp { canvas, _ -> canvas.drawText(text, x, baseline, markerPaint) }
            }
            MarkerKind.TASK_UNCHECKED, MarkerKind.TASK_CHECKED ->
                checkboxOp(
                    item.kind == MarkerKind.TASK_CHECKED,
                    right,
                    midline,
                )
        }
    }

    private fun checkboxOp(
        checked: Boolean,
        right: Float,
        midline: Float,
    ): PaintOp {
        val size = bodyPx * CHECK_SIZE_EM
        val box = RectF(right - size, midline - size / 2, right, midline + size / 2)
        val radius = px(Spacing.Xs.value / 2)
        val tick =
            Path().apply {
                moveTo(box.left + size * CHECK_P1_X, box.top + size * CHECK_P1_Y)
                lineTo(box.left + size * CHECK_P2_X, box.top + size * CHECK_P2_Y)
                lineTo(box.left + size * CHECK_P3_X, box.top + size * CHECK_P3_Y)
            }
        val tickPaint = Paint(mutedStroke).apply { color = palette.page.toArgb() }
        return PaintOp { canvas, _ ->
            if (checked) {
                canvas.drawRoundRect(box, radius, radius, accentFill)
                canvas.drawPath(tick, tickPaint)
            } else {
                canvas.drawRoundRect(box, radius, radius, mutedStroke)
            }
        }
    }

    private fun barOp(item: DrawBar): PaintOp {
        val width = px(Spacing.Xs.value)
        val rect = RectF(item.x.toFloat(), item.y.toFloat(), item.x + width, (item.y + item.height).toFloat())
        val paint = if (item.kind == BarKind.CALLOUT) accentFill else ruleFill
        return PaintOp { canvas, _ -> canvas.drawRoundRect(rect, width / 2, width / 2, paint) }
    }

    private fun codeSurfaceOp(item: DrawCodeSurface): PaintOp {
        val rect =
            RectF(item.x.toFloat(), item.y.toFloat(), (item.x + item.width).toFloat(), (item.y + item.height).toFloat())
        val radius = px(Spacing.CodeRadius.value)
        return PaintOp { canvas, _ -> canvas.drawRoundRect(rect, radius, radius, fill) }
    }

    private fun ruleOp(item: DrawRule): PaintOp {
        val y = item.y + item.height / 2f
        val x0 = item.x.toFloat()
        val x1 = (item.x + item.width).toFloat()
        return PaintOp { canvas, _ -> canvas.drawLine(x0, y, x1, y, ruleStroke) }
    }

    /** Frame for an atomic block with no renderer of its own. */
    private fun atomicOp(item: DrawAtomic): PaintOp {
        val rect =
            RectF(item.x.toFloat(), item.y.toFloat(), (item.x + item.width).toFloat(), (item.y + item.height).toFloat())
        val radius = px(Spacing.CodeRadius.value)
        return PaintOp { canvas, _ ->
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, ruleStroke)
        }
    }

    /**
     * An image (SYN-08, PAG-07): its pixels scaled into the box the layout reserved, or the frame with the alt text
     * where there are none. While a known image is still decoding only the frame shows. Draw never waits: it asks
     * the renderer, which answers from its cache and starts a decode otherwise.
     */
    private fun imageOp(
        item: DrawAtomic,
        block: ImageBlock,
    ): PaintOp {
        val rect =
            RectF(item.x.toFloat(), item.y.toFloat(), (item.x + item.width).toFloat(), (item.y + item.height).toFloat())
        val radius = px(Spacing.CodeRadius.value)
        val renderer = images
        val alt = if (block.alt.isBlank()) null else altLayout(block.alt, item.width)
        images?.warm(block, item.width, item.height)
        return PaintOp { canvas, _ ->
            val bitmap = renderer?.bitmapFor(block, item.width, item.height)
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
            } else {
                canvas.drawRoundRect(rect, radius, radius, fill)
                canvas.drawRoundRect(rect, radius, radius, ruleStroke)
                if (alt != null && (renderer == null || renderer.showsAlt(block, item.width, item.height))) {
                    canvas.save()
                    canvas.clipRect(rect)
                    canvas.translate(rect.left + cellPadding, rect.top + cellPadding)
                    alt.draw(canvas)
                    canvas.restore()
                }
            }
        }
    }

    private fun atomicOrMediaOp(item: DrawAtomic): PaintOp =
        when (val block = item.block) {
            is ImageBlock -> imageOp(item, block)
            is MathBlock -> mathOp(item, block)
            is FrontMatter -> frontMatterOp(item, block)
            else -> atomicOp(item)
        }

    /**
     * The metadata card (SYN-13, PAG-07): the surface frame with the title and then the rows, laid out at the width
     * and with the roles [FrontMatterCard.height] measured, so they fill the box the layout reserved. A box the
     * layout had to cap (85% of the page) clips what does not fit.
     */
    private fun frontMatterOp(
        item: DrawAtomic,
        block: FrontMatter,
    ): PaintOp {
        val rect =
            RectF(item.x.toFloat(), item.y.toFloat(), (item.x + item.width).toFloat(), (item.y + item.height).toFloat())
        val radius = px(Spacing.CodeRadius.value)
        val inner = (item.width - 2 * cellPadding).toInt().coerceAtLeast(1)
        val title =
            FrontMatterCard.title(block)?.let {
                factory.layout(it, FrontMatterCard.TITLE_ROLE, inner, TextStyles.NONE, textPalette)
            }
        val rows =
            FrontMatterCard.rows(block).takeIf { it.isNotEmpty() }?.let {
                factory.layout(it, FrontMatterCard.ROWS_ROLE, inner, TextStyles.NONE, textPalette)
            }
        return PaintOp { canvas, _ ->
            canvas.drawRoundRect(rect, radius, radius, fill)
            canvas.drawRoundRect(rect, radius, radius, ruleStroke)
            canvas.save()
            canvas.clipRect(rect)
            canvas.translate(rect.left + cellPadding, rect.top + cellPadding)
            if (title != null) {
                title.draw(canvas)
                canvas.translate(0f, title.height + cellPadding)
            }
            rows?.draw(canvas)
            canvas.restore()
        }
    }

    private val mathPaint =
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(palette.ink.toArgb(), PorterDuff.Mode.SRC_IN)
        }

    /**
     * Display math (SYN-12, PAG-07): the formula as an alpha mask the size of its box, tinted with the theme
     * ink, so it is centered by the layout and legible on every theme. Where the formula fails the box shows the TeX
     * source in the code style with a quiet error mark. While it is still being measured an unsized box keeps the
     * frame (the same box for every formula, so a late result moves nothing) and a sized one stays blank.
     */
    private fun mathOp(
        item: DrawAtomic,
        block: MathBlock,
    ): PaintOp {
        val rect =
            RectF(item.x.toFloat(), item.y.toFloat(), (item.x + item.width).toFloat(), (item.y + item.height).toFloat())
        val radius = px(Spacing.CodeRadius.value)
        val renderer = math
        renderer?.warm(block, item.width, item.height)
        val mark = bodyPx * MARK_EM
        val source =
            lazy {
                val room = (item.width - MARK_PADDINGS * cellPadding - mark).toInt().coerceAtLeast(1)
                factory.layout(block.tex, TextRole.CODE, room, TextStyles.NONE, textPalette)
            }
        return PaintOp { canvas, _ ->
            val bitmap = renderer?.bitmapFor(block, item.width, item.height)
            val failed = renderer == null || renderer.hasFailed(block)
            val measured = renderer?.sizes?.value?.sizeOf(block) is MathSize.Known
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, rect, mathPaint)
            } else if (failed || !measured) {
                canvas.drawRoundRect(rect, radius, radius, fill)
                canvas.drawRoundRect(rect, radius, radius, ruleStroke)
                if (failed) {
                    canvas.save()
                    canvas.clipRect(rect)
                    canvas.translate(rect.left + cellPadding, rect.top + cellPadding)
                    source.value.draw(canvas)
                    canvas.restore()
                    // The box is the layout's and cannot grow, so a source that does not fit says so (A11Y-04
                    // still reads all of it): an ellipsis over the last visible line.
                    if (source.value.height > rect.height() - 2 * cellPadding) ellipsis(canvas, rect, source.value)
                    if (renderer != null) {
                        errorMark(canvas, rect.right - cellPadding - mark, rect.top + cellPadding, mark)
                    }
                }
            }
        }
    }

    private fun ellipsis(
        canvas: Canvas,
        rect: RectF,
        source: Layout,
    ) {
        val text = source.paint
        val dots = "…"
        val width = text.measureText(dots)
        val right = rect.right - cellPadding
        val baseline = rect.bottom - cellPadding - text.descent()
        canvas.drawRect(right - width - cellPadding, baseline + text.ascent(), right, baseline + text.descent(), fill)
        canvas.drawText(dots, right - width, baseline, text)
    }

    /** The design system's `circle-alert` (24 grid, its own path data) in muted ink: one icon, not a copy of it. */
    private val errorMarkPaths: List<Path> =
        BifolioIcons.CircleAlert.root.mapNotNull { (it as? VectorPath)?.pathData?.toPath()?.asAndroidPath() }

    private fun errorMark(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
    ) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / MARK_GRID, size / MARK_GRID)
        for (path in errorMarkPaths) canvas.drawPath(path, markStroke)
        canvas.restore()
    }

    private fun altLayout(
        alt: String,
        boxWidth: Int,
    ): Layout =
        factory.layout(
            alt,
            TextRole.TABLE_CELL,
            (boxWidth - 2 * cellPadding).toInt().coerceAtLeast(1),
            TextStyles.NONE,
            textPalette,
        )

    private fun titleOp(item: DrawCalloutTitle): PaintOp {
        val text = labels[item.kind]
        val baseline = item.y - titlePaint.fontMetrics.ascent
        val x = item.x.toFloat()
        return PaintOp { canvas, _ -> canvas.drawText(text, x, baseline, titlePaint) }
    }

    /**
     * Three quiet dots level with the first line of a block continued from the previous page. They hang in the
     * margin when the block is indented, but never leave the column: at the column edge (code padding, tables)
     * they sit halfway into the block's own inset, so they cannot reach the gutter or fold band (LAY-14).
     */
    private fun continuationOp(item: DrawContinuation): PaintOp {
        val r = bodyPx * DOT_RADIUS_EM
        val inset = if (item.x == 0) cellPadding / 2f else item.x / 2f
        val cx = maxOf(item.x - bodyPx * CONTINUATION_OFFSET_EM, inset, r)
        val cy = item.y + item.height / 2f
        val step = r * DOT_STEP
        return PaintOp { canvas, _ ->
            for (i in -1..1) canvas.drawCircle(cx, cy + i * step, r, mutedFill)
        }
    }

    private fun ornamentOp(
        draw: PageDraw,
        columnHeight: Int,
    ): PaintOp {
        val r = bodyPx * DOT_RADIUS_EM
        val spacing = bodyPx * ORNAMENT_SPACING_EM
        val cx = draw.columnWidth / 2f
        val cy = minOf(draw.usedHeight + bodyPx * ORNAMENT_DROP_EM, columnHeight - r)
        return PaintOp { canvas, _ ->
            val first = -(ORNAMENT_DOTS - 1) / 2f
            for (i in 0 until ORNAMENT_DOTS) canvas.drawCircle(cx + (first + i) * spacing, cy, r, mutedFill)
        }
    }

    private fun tableRowOp(row: DrawTableRow): PaintOp {
        val pad = cellPadding
        val scrolls = row.tableWidth > row.columnWidth
        val cells =
            row.cells.mapNotNull { cell ->
                cell.leaf?.let { Cell(cell.x, cell.alignment, layoutOf(it), it.width, LeafHits(layoutOf(it), it)) }
            }
        val top = row.y.toFloat()
        val bottom = (row.y + row.height).toFloat()
        val left = row.x.toFloat()
        val right = (row.x + row.columnWidth).toFloat()
        val maxScroll = (row.tableWidth - row.columnWidth).coerceAtLeast(0)
        val block = row.blockIndex
        val header = row.header
        return object : HighlightingOp {
            override fun draw(
                canvas: Canvas,
                tableScroll: (Int) -> Int,
                highlights: PageHighlights?,
            ) {
                val scroll = if (scrolls) tableScroll(block).coerceIn(0, maxScroll) else 0
                canvas.save()
                canvas.clipRect(left, top, right, bottom)
                if (header) canvas.drawRect(left, top, right, bottom, fill)
                for (cell in cells) {
                    val x = left + cell.x + pad - scroll + alignOffset(cell.alignment, cell.width, cell.layout)
                    canvas.save()
                    canvas.translate(x, top + pad)
                    if (highlights != null) cell.hits.paint(canvas, highlights)
                    cell.layout.draw(canvas)
                    canvas.restore()
                }
                canvas.drawLine(left, bottom, right, bottom, ruleStroke)
                if (scrolls) {
                    canvas.drawLine(left, top, left, bottom, ruleStroke)
                    canvas.drawLine(right, top, right, bottom, ruleStroke)
                }
                canvas.restore()
            }
        }
    }

    private class Cell(
        val x: Int,
        val alignment: ColumnAlignment,
        val layout: Layout,
        val width: Int,
        val hits: LeafHits,
    )

    private fun alignOffset(
        alignment: ColumnAlignment,
        width: Int,
        layout: Layout,
    ): Float {
        var widest = 0f
        for (i in 0 until layout.lineCount) widest = maxOf(widest, layout.getLineWidth(i))
        val free = (width - widest).coerceAtLeast(0f)
        return when (alignment) {
            ColumnAlignment.RIGHT -> free
            ColumnAlignment.CENTER -> free / 2
            else -> 0f
        }
    }

    private companion object {
        const val LEAF_CACHE = 48
        const val LOAD_FACTOR = 0.75f
        const val MARK_ALPHA = 0.25f
    }
}
