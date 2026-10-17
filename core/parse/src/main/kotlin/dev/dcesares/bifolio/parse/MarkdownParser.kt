package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.plainText
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
                ),
            ).includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build()

    /**
     * Parses [text]. Line endings are normalized first (DOC-04), so [Document.source] and every
     * block range refer to the normalized text. Pure CPU work: call it off the main thread.
     */
    public fun parse(text: String): Document {
        val source = TextDecoder.normalizeLineEndings(text)
        val root = parser.parse(source)
        val converter = BlockConverter(source.length, ::summaryInlines)
        val blocks = converter.convertChildren(root, 0)
        val title =
            blocks
                .firstNotNullOfOrNull { (it as? Heading)?.takeIf { h -> h.level == 1 } }
                ?.let { plainText(it.content).trim() }
        return Document(title, source, blocks)
    }

    private fun summaryInlines(markdown: String): List<Inline> {
        val blocks = BlockConverter(markdown.length) { emptyList() }.convertChildren(parser.parse(markdown), 0)
        return (blocks.singleOrNull() as? Paragraph)?.content ?: InlineConverter.plainLines(markdown)
    }
}
