package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.searchHit
import dev.dcesares.bifolio.designsystem.searchHitFocused
import dev.dcesares.bifolio.layout.HighlightRange
import dev.dcesares.bifolio.layout.SearchHighlighter
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.Inline

/** The search hits of the scroll surface (UI-08). Null when no search is on screen: nothing is painted then. */
internal val LocalSearchHighlighter = compositionLocalOf<SearchHighlighter?> { null }

/**
 * Where footnote reference labels sit in the text of [inlines]. The scroll surface draws a label ("1") that the
 * rendered text the search read leaves out, so a hit found in rendered characters moves right by the labels in front
 * of it. Pure; tested on the JVM.
 */
internal class FootnoteShift(
    inlines: List<Inline>,
) {
    private val positions = ArrayList<Int>(0)
    private val lengths = ArrayList<Int>(0)
    private var rendered = 0

    init {
        walk(inlines)
    }

    private fun walk(inlines: List<Inline>) {
        for (inline in inlines) {
            when (inline) {
                is Inline.Text -> rendered += inline.text.length
                is Inline.Code -> rendered += inline.text.length
                is Inline.Image -> rendered += inline.alt.length
                is Inline.InlineMath -> rendered += inline.tex.length
                is Inline.Emphasis -> walk(inline.children)
                is Inline.Strong -> walk(inline.children)
                is Inline.Strikethrough -> walk(inline.children)
                is Inline.Link -> walk(inline.children)
                is Inline.Styled -> walk(inline.children)
                Inline.SoftBreak, Inline.HardBreak -> rendered += 1
                is Inline.FootnoteReference -> {
                    positions.add(rendered)
                    lengths.add(inline.label.length)
                }
            }
        }
    }

    /** The drawn index of the rendered character at [renderedIndex]: labels right before it come first. */
    fun start(renderedIndex: Int): Int = renderedIndex + shift { it <= renderedIndex }

    /** The drawn index just after a hit that ends at [renderedEnd] (exclusive): a label right after it stays out. */
    fun end(renderedEnd: Int): Int = renderedEnd + shift { it < renderedEnd }

    private inline fun shift(counts: (Int) -> Boolean): Int {
        var total = 0
        for (i in positions.indices) if (counts(positions[i])) total += lengths[i]
        return total
    }
}

/**
 * Text of a block in the scroll surface, with the search hits painted behind it. With no search on screen this is
 * a plain [BasicText] and nothing is allocated for hits. Hits are found in the rendered text of [block] by the
 * highlighter and drawn from the text layout, so typing or stepping through results never relays out the page.
 */
@Composable
internal fun HighlightedText(
    text: AnnotatedString,
    block: Block?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    softWrap: Boolean = true,
    inlines: List<Inline>? = null,
) {
    val highlighter = LocalSearchHighlighter.current
    val ranges = remember(highlighter, block) { highlighter?.let { h -> block?.let { h.hitsIn(it) } }.orEmpty() }
    val surface = remember { TextSurface() }
    val tappable = modifier.noteTaps(text, surface, LocalNoteHandler.current)
    if (ranges.isEmpty()) {
        BasicText(text, tappable, style, onTextLayout = { surface.layout = it }, softWrap = softWrap)
        return
    }
    val shift = remember(inlines, ranges) { inlines?.let { FootnoteShift(it) } }
    val palette = BifolioTheme.palette
    val hit = palette.searchHit
    val focused = palette.searchHitFocused
    val outline = palette.accent
    BasicText(
        text,
        tappable.drawBehind {
            val result = surface.layout ?: return@drawBehind
            for (range in ranges) if (!range.focused) paint(result, range, shift, text.length, hit, null)
            for (range in ranges) if (range.focused) paint(result, range, shift, text.length, focused, outline)
        },
        style,
        onTextLayout = { surface.layout = it },
        softWrap = softWrap,
    )
}

private fun DrawScope.paint(
    layout: TextLayoutResult,
    range: HighlightRange,
    shift: FootnoteShift?,
    length: Int,
    fill: Color,
    outline: Color?,
) {
    val start = (shift?.start(range.start) ?: range.start).coerceIn(0, length)
    val end = (shift?.end(range.end) ?: range.end).coerceIn(start, length)
    if (end == start) return
    val path = layout.getPathForRange(start, end)
    drawPath(path, fill)
    if (outline != null) drawPath(path, outline, style = Stroke(width = Spacing.Hairline.toPx()))
}
