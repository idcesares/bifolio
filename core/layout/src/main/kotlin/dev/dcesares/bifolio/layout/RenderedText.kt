package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.SourceRange

/** Rendered text of a text block and, for each of its characters, the source offset it comes from. */
internal class Rendered(
    val text: String,
    /** `text.length + 1` non-decreasing source offsets; the last one is where the text ends. */
    val map: IntArray,
)

/**
 * Builds the rendered text of inlines together with its map to source offsets (ADR-0005), in one pass.
 * Each inline that renders text is aligned inside its own source range, never beyond the end of the
 * previous one, so text can not leak into link destinations or markup. Inlines without a range fall back to
 * a greedy match over the rest of the block.
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
        val builder = Builder(source, from, maxOf(to, from))
        builder.walk(inlines)
        return builder.finish()
    }

    private class Builder(
        private val source: String,
        from: Int,
        private val to: Int,
    ) {
        private val sb = StringBuilder()
        private var map = IntArray(INITIAL)
        private var cursor = from

        fun walk(inlines: List<Inline>) {
            for (inline in inlines) {
                when (inline) {
                    is Inline.Text -> leaf(inline.text, inline.range, decode = true)
                    is Inline.Code -> leaf(inline.text, inline.range, decode = false)
                    is Inline.Image -> leaf(inline.alt, inline.range, decode = true)
                    is Inline.InlineMath -> leaf(inline.tex, inline.range, decode = false)
                    is Inline.Emphasis -> walk(inline.children)
                    is Inline.Strong -> walk(inline.children)
                    is Inline.Strikethrough -> walk(inline.children)
                    is Inline.Link -> walk(inline.children)
                    is Inline.Styled -> walk(inline.children)
                    Inline.SoftBreak, Inline.HardBreak -> breakChar()
                    is Inline.FootnoteReference -> Unit
                }
            }
        }

        private fun breakChar() {
            reserve(1)
            map[sb.length] = cursor
            sb.append('\n')
        }

        private fun leaf(
            text: String,
            range: SourceRange?,
            decode: Boolean,
        ) {
            if (text.isEmpty()) return
            val at = sb.length
            reserve(text.length)
            sb.append(text)
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
            reserve(0)
            map[sb.length] = cursor
            return Rendered(sb.toString(), map.copyOf(sb.length + 1))
        }
    }
}
