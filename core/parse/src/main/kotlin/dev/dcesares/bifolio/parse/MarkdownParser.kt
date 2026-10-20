package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/** Markdown text to the immutable [Document] model with commonmark-java (ADR-0003, DOC-06). Thread-safe. */
public object MarkdownParser {
    private val parser: Parser =
        Parser
            .builder()
            .extensions(
                listOf(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    TaskListItemsExtension.create(),
                    FootnotesExtension.create(),
                ),
            ).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build()

    /**
     * Parses [text]. Line endings are normalized first (DOC-04), so [Document.source] and every
     * block range refer to the normalized text. Pure CPU work: call it off the main thread.
     */
    public fun parse(text: String): Document {
        val source = TextDecoder.normalizeLineEndings(text).removePrefix("﻿")
        val front = FrontMatterScanner.scan(source)
        val root = parser.parse(if (front == null) source else FrontMatterScanner.blank(source, front))
        val converter = BlockConverter(source, ::summaryInlines)
        val blocks = listOfNotNull(front) + converter.convertChildren(root, 0)
        val title =
            front?.title
                ?: blocks
                    .firstNotNullOfOrNull { (it as? Heading)?.takeIf { h -> h.level == 1 } }
                    ?.let { plainText(it.content).trim() }
        return Document(title, source, blocks, front?.language)
    }

    private fun summaryInlines(markdown: String): List<Inline> {
        val blocks = BlockConverter(markdown) { emptyList() }.convertChildren(parser.parse(markdown), 0)
        return (blocks.singleOrNull() as? Paragraph)?.content ?: InlineConverter(markdown).plainLines(markdown)
    }
}
