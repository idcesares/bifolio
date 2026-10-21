package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.SourceRange
import org.commonmark.node.CustomNode
import org.commonmark.node.Text
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.Position
import org.commonmark.parser.beta.Scanner

/**
 * Inline node produced by [MathInlineParserFactory]: TeX source of a `$...$` or `$$...$$` pair (SYN-12).
 * [start] and [end] are offsets in the parsed input of the TeX source (delimiters excluded), or -1 when unknown.
 */
internal class MathNode(
    val tex: String,
    val display: Boolean,
    val start: Int,
    val end: Int,
) : CustomNode()

/** Window of the TeX source of [this] in a source of [length] characters, delimiters excluded; null if unknown. */
internal fun MathNode.sourceRange(length: Int): SourceRange? {
    if (start < 0 || end < start) return null
    val s = start.coerceIn(0, length)
    return SourceRange(s, end.coerceIn(s, length))
}

/**
 * Hand-written scanner for SYN-12 on top of commonmark's inline extension point (no dependency). commonmark has
 * already handled backslash escapes and code spans before a `$` is seen, and the scanner never leaves its
 * paragraph, so escapes stay literal, code is untouched and math never spans paragraphs.
 *
 * Rules, all decided with one forward pass (no backtracking, so a long paragraph of `$` is linear):
 * - a run of one `$` opens inline math, a run of two opens display math, three or more are literal text;
 * - inline: the opener is followed by a non-whitespace character on the same line; the closer is the next
 *   unescaped `$` run, it is one `$`, is not preceded by whitespace and not followed by a digit;
 * - display: the closer is the next unescaped `$` run, it is two `$`; the TeX may cover several lines and must
 *   not be blank;
 * - inside a formula `\` and the character after it are skipped, so `\$` does not close;
 * - when the closer fails, the opener stays literal and no later closer is tried.
 */
internal class MathInlineParserFactory : InlineContentParserFactory {
    override fun getTriggerCharacters(): Set<Char> = setOf(DOLLAR)

    override fun create(): InlineContentParser = MathInlineParser()
}

private const val DOLLAR = '$'
private const val BACKSLASH = '\\'
private const val NEWLINE = '\n'
private const val MAX_SINGLE = 1
private const val MAX_DOUBLE = 2

private class MathInlineParser : InlineContentParser {
    override fun tryParse(state: InlineParserState): ParsedInline? {
        val scanner = state.scanner()
        val before = scanner.position()
        val run = scanner.matchMultiple(DOLLAR)
        val afterRun = scanner.position()
        val parsed =
            if (run == MAX_SINGLE || run == MAX_DOUBLE) tryPair(scanner, run == MAX_DOUBLE) else null
        if (parsed != null) return parsed
        // Literal. A run of two or more is consumed whole so its second dollar can not open anything.
        if (run == MAX_SINGLE) {
            scanner.setPosition(before)
            return ParsedInline.none()
        }
        return literal(scanner, before, afterRun)
    }

    private fun literal(
        scanner: Scanner,
        from: Position,
        to: Position,
    ): ParsedInline {
        scanner.setPosition(to)
        val source = scanner.getSource(from, to)
        val text = Text(source.content)
        text.setSourceSpans(source.sourceSpans)
        return ParsedInline.of(text, to)
    }

    /** Scans from just after the opener; returns the node, or null when there is no valid closer. */
    private fun tryPair(
        scanner: Scanner,
        display: Boolean,
    ): ParsedInline? {
        if (!display && !startsInline(scanner)) return null
        val start = scanner.position()
        val end = findCloser(scanner, display) ?: return null
        val closed = scanner.position()
        val body = scanner.getSource(start, end)
        val tex = body.content
        if (tex.isBlank()) return null
        val spans = body.sourceSpans
        val first = spans.firstOrNull()
        val last = spans.lastOrNull()
        val from = first?.inputIndex ?: -1
        val to = if (last == null) -1 else last.inputIndex + last.length
        val node = MathNode(tex, display, from, to)
        return ParsedInline.of(node, closed)
    }

    private fun startsInline(scanner: Scanner): Boolean {
        if (!scanner.hasNext()) return false
        val next = scanner.peek()
        return next != NEWLINE && !next.isWhitespace()
    }

    /**
     * Moves the scanner to just after the closing run and returns the position where the TeX ends, or returns
     * null (scanner position unspecified) when the closer is missing or invalid.
     */
    private fun findCloser(
        scanner: Scanner,
        display: Boolean,
    ): Position? {
        while (scanner.hasNext()) {
            val c = scanner.peek()
            when {
                c == NEWLINE && !display -> return null
                c == BACKSLASH -> skipEscape(scanner)
                c == DOLLAR -> return closerAt(scanner, display)
                else -> scanner.next()
            }
        }
        return null
    }

    private fun skipEscape(scanner: Scanner) {
        scanner.next()
        if (scanner.hasNext()) scanner.next()
    }

    private fun closerAt(
        scanner: Scanner,
        display: Boolean,
    ): Position? {
        val end = scanner.position()
        val previous = scanner.peekPreviousCodePoint()
        val run = scanner.matchMultiple(DOLLAR)
        val valid =
            if (display) {
                run == MAX_DOUBLE
            } else {
                run == MAX_SINGLE && !Character.isWhitespace(previous) && !followedByDigit(scanner)
            }
        return if (valid) end else null
    }

    private fun followedByDigit(scanner: Scanner): Boolean = scanner.hasNext() && scanner.peek() in '0'..'9'
}
