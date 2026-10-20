package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.MermaidFence
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.Table

/** Context of a match: [text] has no newlines; the match is `text[matchStart until matchEnd]`. */
public data class SearchSnippet(
    val text: String,
    val matchStart: Int,
    val matchEnd: Int,
)

/**
 * One occurrence of the query. [anchor] is the source position of the first matched character (ANC-01);
 * [length] counts rendered characters (what is drawn, for highlights), [sourceLength] counts source characters
 * from the anchor to the end of the match. [blockIndex] is the top level block and equals `anchor.blockIndex`.
 */
public data class SearchMatch(
    val anchor: Anchor,
    val blockIndex: Int,
    val length: Int,
    val sourceLength: Int,
    val snippet: SearchSnippet,
)

/** Matches in reading order; [truncated] is true when more than the requested limit exist. */
public data class SearchResult(
    val matches: List<SearchMatch>,
    val truncated: Boolean,
)

/**
 * Text search over a [Document] (UI-08). Pure and stateless; run it on a background dispatcher.
 *
 * Case and diacritics are ignored (see [SearchFolding]); matches stay inside one text unit (paragraph, heading,
 * details summary, table cell, code block), so they never span blocks, cells or code/prose boundaries. Searched:
 * paragraphs, headings, list items, quotes, callouts, footnote definitions, details (closed ones too), table
 * cells, code and mermaid blocks. Not searched: front matter, math, image alt text of block images, callout labels.
 */
public object DocumentSearch {
    public const val DEFAULT_LIMIT: Int = 500

    /**
     * Finds [query] in [document], at most [limit] matches. A blank query finds nothing.
     * [checkCancelled] is called before each text unit; make it throw to cancel (for coroutines pass
     * `{ job.ensureActive() }` with the caller's `Job`).
     */
    public fun search(
        document: Document,
        query: String,
        limit: Int = DEFAULT_LIMIT,
        checkCancelled: () -> Unit = {},
    ): SearchResult {
        val needle = SearchFolding.fold(query.trim()).text
        if (needle.isEmpty() || limit <= 0) return SearchResult(emptyList(), false)
        val run = Run(document, needle, limit, checkCancelled)
        for ((i, block) in document.blocks.withIndex()) {
            if (run.full) break
            run.block(i, block)
        }
        return SearchResult(run.matches, run.truncated)
    }

    private class Run(
        val doc: Document,
        val needle: String,
        val limit: Int,
        val check: () -> Unit,
    ) {
        val matches = ArrayList<SearchMatch>()
        var truncated = false
        val full: Boolean get() = truncated

        fun block(
            top: Int,
            block: Block,
        ) {
            when (block) {
                is Heading -> text(top, RenderedText.of(block.content, doc.source, block.range.start, block.range.end))
                is Paragraph ->
                    text(top, RenderedText.of(block.content, doc.source, block.range.start, block.range.end))
                is CodeBlock -> code(top, block.code, block, block.fenced)
                is MermaidFence -> code(top, block.source, block, true)
                is Table -> table(top, block)
                is ListBlock -> block.items.forEach { item -> children(top, item.blocks) }
                is BlockQuote -> children(top, block.blocks)
                is Callout -> children(top, block.blocks)
                is FootnoteDefinition -> children(top, block.blocks)
                is HtmlDetails -> {
                    text(top, RenderedText.of(block.summary, doc.source, block.range.start, block.range.end))
                    children(top, block.blocks)
                }
                else -> Unit
            }
        }

        private fun children(
            top: Int,
            blocks: List<Block>,
        ) {
            for (child in blocks) {
                if (full) return
                block(top, child)
            }
        }

        private fun code(
            top: Int,
            code: String,
            block: Block,
            fenced: Boolean,
        ) {
            val nl = doc.source.indexOf('\n', block.range.start)
            val from = if (fenced && nl in 0 until block.range.end) nl + 1 else block.range.start
            text(top, Rendered(code, CodeSourceMap.build(code, doc.source, from, block.range.end)))
        }

        /** Cells have no source range: each is aligned, in reading order, inside the rest of the table source. */
        private fun table(
            top: Int,
            table: Table,
        ) {
            var cursor = table.range.start
            val end = table.range.end
            for (row in sequenceOf(table.header) + table.rows) {
                for (cell in row.cells) {
                    if (full) return
                    val plain = RenderedText.cell(cell.content).text
                    val map = LeafAligner.build(plain, doc.source, cursor, end)
                    text(top, Rendered(plain, map))
                    cursor = map[plain.length].coerceIn(cursor, end)
                }
            }
        }

        private fun text(
            top: Int,
            rendered: Rendered,
        ) {
            check()
            if (rendered.text.length < needle.length) return
            val folded = SearchFolding.fold(rendered.text)
            val base = doc.blocks[top].range.start
            var at = folded.text.indexOf(needle)
            while (at >= 0) {
                if (matches.size >= limit) {
                    truncated = true
                    return
                }
                val start = folded.origStart[at]
                val end = folded.origEnd[at + needle.length - 1]
                val first = rendered.map[start]
                val last = if (end > start) rendered.map[end - 1] + 1 else rendered.map[end]
                matches.add(
                    SearchMatch(
                        anchor = Anchor(top, maxOf(first - base, 0)),
                        blockIndex = top,
                        length = end - start,
                        sourceLength = maxOf(last - first, 0),
                        snippet = SearchSnippets.of(rendered.text, start, end),
                    ),
                )
                at = folded.text.indexOf(needle, at + needle.length)
            }
        }
    }
}
