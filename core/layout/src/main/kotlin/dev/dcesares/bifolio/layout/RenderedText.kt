package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.InlineStyle
import dev.dcesares.bifolio.model.SourceRange

/** Rendered text of a text block, for each of its characters the source offset it comes from, and its styling. */
internal class Rendered(
    val text: String,
    /** `text.length + 1` non-decreasing source offsets; the last one is where the text ends. */
    val map: IntArray,
    val styles: TextStyles = TextStyles.NONE,
)

/**
 * Builds the rendered text of inlines together with its map to source offsets (ADR-0005) and its style runs,
 * in one pass. Each inline that renders text is aligned inside its own source range, never beyond the end of
 * the previous one, so text can not leak into link destinations or markup. Inlines without a range fall back
 * to a greedy match over the rest of the block.
 */
internal object RenderedText {
    private const val LOOKAHEAD = 512
    private const val INITIAL = 64

    fun of(
        inlines: List<Inline>,
        source: String,
        from: Int,
        to: Int,
    ): Rendered {
        val builder = Builder(source, from, maxOf(to, from), mapped = true, softBreak = ' ')
        builder.walk(inlines, 0)
        return builder.finish()
    }

    /** Text and styles of a table cell, which has no source range (no mapping). Soft breaks are newlines. */
    fun cell(inlines: List<Inline>): Rendered {
        val builder = Builder("", 0, 0, mapped = false, softBreak = '\n')
        builder.walk(inlines, 0)
        return builder.finish()
    }

    private class Builder(
        private val source: String,
        from: Int,
        private val to: Int,
        private val mapped: Boolean,
        private val softBreak: Char,
    ) {
        private val sb = StringBuilder()
        private var map = if (mapped) IntArray(INITIAL) else IntArray(0)
        private var cursor = from
        private var runStarts = IntArray(RUNS)
        private var runEnds = IntArray(RUNS)
        private var runFlags = IntArray(RUNS)
        private var runCount = 0

        fun walk(
            inlines: List<Inline>,
            flags: Int,
        ) {
            for (inline in inlines) {
                when (inline) {
                    is Inline.Text -> leaf(inline.text, inline.range, decode = true, flags = flags)
                    is Inline.Code -> leaf(inline.text, inline.range, decode = false, flags = flags or StyleFlag.CODE)
                    is Inline.Image -> leaf(inline.alt, inline.range, decode = true, flags = flags)
                    is Inline.InlineMath ->
                        leaf(inline.tex, inline.range, decode = false, flags = flags or StyleFlag.CODE)
                    is Inline.Emphasis -> walk(inline.children, flags or StyleFlag.ITALIC)
                    is Inline.Strong -> walk(inline.children, flags or StyleFlag.BOLD)
                    is Inline.Strikethrough -> walk(inline.children, flags or StyleFlag.STRIKETHROUGH)
                    is Inline.Link -> walk(inline.children, flags or StyleFlag.LINK)
                    is Inline.Styled -> walk(inline.children, flags or flagOf(inline.style))
                    Inline.SoftBreak -> breakChar(softBreak)
                    Inline.HardBreak -> breakChar('\n')
                    is Inline.FootnoteReference -> Unit
                }
            }
        }

        private fun flagOf(style: InlineStyle): Int =
            when (style) {
                InlineStyle.SUBSCRIPT -> StyleFlag.SUBSCRIPT
                InlineStyle.SUPERSCRIPT -> StyleFlag.SUPERSCRIPT
                InlineStyle.KEYBOARD -> StyleFlag.CODE
                InlineStyle.MARK -> StyleFlag.MARK
                InlineStyle.UNDERLINE -> StyleFlag.UNDERLINE
            }

        private fun breakChar(rendered: Char) {
            if (mapped) {
                reserve(1)
                map[sb.length] = cursor
            }
            sb.append(rendered)
        }

        private fun run(
            start: Int,
            end: Int,
            flags: Int,
        ) {
            if (flags == 0 || end <= start) return
            if (runCount > 0 && runEnds[runCount - 1] == start && runFlags[runCount - 1] == flags) {
                runEnds[runCount - 1] = end
                return
            }
            if (runCount == runStarts.size) {
                runStarts = runStarts.copyOf(runCount * 2)
                runEnds = runEnds.copyOf(runCount * 2)
                runFlags = runFlags.copyOf(runCount * 2)
            }
            runStarts[runCount] = start
            runEnds[runCount] = end
            runFlags[runCount] = flags
            runCount++
        }

        private fun leaf(
            text: String,
            range: SourceRange?,
            decode: Boolean,
            flags: Int,
        ) {
            if (text.isEmpty()) return
            val at = sb.length
            sb.append(text)
            run(at, sb.length, flags)
            if (!mapped) return
            reserve(text.length)
            if (range == null) {
                cursor = LeafAligner.align(text, source, cursor, to, LOOKAHEAD, map, at, decode)
                return
            }
            val start = minOf(maxOf(range.start, cursor), to)
            val end = minOf(maxOf(range.end, start), to)
            if (start == range.start &&
                end - start == text.length &&
                source.regionMatches(start, text, 0, text.length)
            ) {
                for (i in text.indices) map[at + i] = start + i
            } else {
                LeafAligner.align(text, source, start, end, Int.MAX_VALUE, map, at, decode)
            }
            cursor = end
        }

        private fun reserve(extra: Int) {
            val needed = sb.length + extra + 1
            if (needed > map.size) map = map.copyOf(maxOf(needed, map.size * 2))
        }

        fun finish(): Rendered {
            val styles =
                if (runCount == 0) {
                    TextStyles.NONE
                } else {
                    TextStyles(runStarts.copyOf(runCount), runEnds.copyOf(runCount), runFlags.copyOf(runCount))
                }
            if (!mapped) return Rendered(sb.toString(), IntArray(0), styles)
            reserve(0)
            map[sb.length] = cursor
            return Rendered(sb.toString(), map.copyOf(sb.length + 1), styles)
        }
    }

    private const val RUNS = 8
}
