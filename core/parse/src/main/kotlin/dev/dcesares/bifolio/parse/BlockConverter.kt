package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.ColumnAlignment
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.ListItem
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.SourceRange
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.TableCell
import dev.dcesares.bifolio.model.TableRow
import dev.dcesares.bifolio.model.TaskState
import dev.dcesares.bifolio.model.ThematicBreak
import dev.dcesares.bifolio.model.plainText
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HtmlBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.ext.gfm.tables.TableCell as CmTableCell
import org.commonmark.ext.gfm.tables.TableRow as CmTableRow
import org.commonmark.node.BlockQuote as CmBlockQuote
import org.commonmark.node.Heading as CmHeading
import org.commonmark.node.ListBlock as CmListBlock
import org.commonmark.node.ListItem as CmListItem
import org.commonmark.node.Paragraph as CmParagraph
import org.commonmark.node.ThematicBreak as CmThematicBreak

/** Nesting cap for lists, quotes and details (DOC-08). */
internal const val MAX_NESTING = 32

/**
 * commonmark block tree to the immutable [Block] model. One instance per parse (it owns the slug state).
 * Containers deeper than [MAX_NESTING] are flattened into their parent, never dropped (DOC-08).
 */
internal class BlockConverter(
    private val sourceLength: Int,
    private val inlineParser: (String) -> List<Inline>,
) {
    private val slugs = SlugGenerator()

    fun convertChildren(
        parent: Node,
        depth: Int,
    ): List<Block> = convertNodes(children(parent), depth)

    private fun convertNodes(
        nodes: List<Node>,
        depth: Int,
    ): List<Block> {
        val out = ArrayList<Block>()
        var i = 0
        while (i < nodes.size) {
            val node = nodes[i]
            i =
                if (node is HtmlBlock && DetailsHtml.opensDetails(node.literal)) {
                    convertDetails(nodes, i, depth, out)
                } else {
                    convertOne(node, depth, out)
                    i + 1
                }
        }
        return out
    }

    private fun convertOne(
        node: Node,
        depth: Int,
        out: MutableList<Block>,
    ) {
        val range = rangeOf(node)
        when (node) {
            is CmHeading -> {
                val content = InlineConverter.convert(node)
                out.add(Heading(node.level, content, slugs.next(plainText(content)), range))
            }
            is CmParagraph -> out.add(Paragraph(InlineConverter.convert(node), range))
            is CmListBlock -> convertList(node, depth, range, out)
            is CmBlockQuote ->
                if (depth >=
                    MAX_NESTING
                ) {
                    out.addAll(convertChildren(node, depth))
                } else {
                    out.add(
                        BlockQuote(
                            convertChildren(
                                node,
                                depth + 1,
                            ),
                            range,
                        ),
                    )
                }
            is FencedCodeBlock -> out.add(CodeBlock(languageOf(node.info), trimOneNewline(node.literal), true, range))
            is IndentedCodeBlock -> out.add(CodeBlock(null, trimOneNewline(node.literal), false, range))
            is CmThematicBreak -> out.add(ThematicBreak(range))
            is HtmlBlock -> out.add(Paragraph(InlineConverter.plainLines(node.literal.trimEnd('\n')), range))
            is TableBlock -> out.add(TableConverter.convert(node, range))
            else -> Unit
        }
    }

    private fun convertList(
        node: CmListBlock,
        depth: Int,
        range: SourceRange,
        out: MutableList<Block>,
    ) {
        if (depth >= MAX_NESTING) {
            for (item in children(node)) out.addAll(convertChildren(item, depth))
            return
        }
        val ordered = node is OrderedList
        val start = if (node is OrderedList) node.markerStartNumber ?: 1 else 1
        val items = children(node).map { item -> convertItem(item as CmListItem, depth + 1) }
        out.add(ListBlock(ordered, start, node.isTight, items, range))
    }

    private fun convertItem(
        item: CmListItem,
        depth: Int,
    ): ListItem {
        val task = taskStateOf(item)
        return ListItem(task, convertChildren(item, depth), rangeOf(item))
    }

    private fun taskStateOf(item: CmListItem): TaskState? {
        val marker =
            item.firstChild as? TaskListItemMarker
                ?: item.firstChild?.firstChild as? TaskListItemMarker
                ?: return null
        return if (marker.isChecked) TaskState.CHECKED else TaskState.UNCHECKED
    }

    /** Builds a [HtmlDetails] starting at nodes[index]; returns the index after the last consumed node. */
    private fun convertDetails(
        nodes: List<Node>,
        index: Int,
        depth: Int,
        out: MutableList<Block>,
    ): Int {
        val open = nodes[index] as HtmlBlock
        val openRange = rangeOf(open)
        val head = DetailsHtml.parseOpening(open.literal)
        val summary = head.summaryMarkdown?.let(inlineParser) ?: emptyList()
        val inner = ArrayList<Block>()
        val innerDepth = depth + 1
        if (head.bodyBeforeClose.isNotBlank()) {
            inner.add(
                Paragraph(InlineConverter.plainLines(head.bodyBeforeClose.trim()), openRange),
            )
        }
        var end = openRange.end
        var next = index + 1
        var trailing: Paragraph? = null
        if (!head.closed) {
            val close = findClosingBlock(nodes, index + 1)
            val stop = if (close < 0) nodes.size else close
            inner.addAll(convertNodes(nodes.subList(index + 1, stop), innerDepth))
            if (close >= 0) {
                val closeBlock = nodes[close] as HtmlBlock
                val closeRange = rangeOf(closeBlock)
                val parts = DetailsHtml.splitAtClose(closeBlock.literal)
                if (parts.before.isNotBlank()) {
                    inner.add(
                        Paragraph(InlineConverter.plainLines(parts.before.trim()), closeRange),
                    )
                }
                if (parts.after.isNotBlank()) {
                    trailing =
                        Paragraph(InlineConverter.plainLines(parts.after.trim()), closeRange)
                }
                end = if (trailing == null) closeRange.end else closeRange.start
                next = close + 1
            } else {
                end = (nodes.subList(index + 1, stop).lastOrNull()?.let { rangeOf(it).end } ?: openRange.end)
                next = stop
            }
        } else if (head.after.isNotBlank()) {
            trailing = Paragraph(InlineConverter.plainLines(head.after.trim()), openRange)
        }
        val range = SourceRange(openRange.start, maxOf(openRange.start, end))
        if (depth >= MAX_NESTING) {
            out.addAll(inner)
        } else {
            out.add(HtmlDetails(summary, head.open, inner, range))
        }
        trailing?.let { out.add(it) }
        return next
    }

    private fun findClosingBlock(
        nodes: List<Node>,
        from: Int,
    ): Int {
        var level = 1
        for (i in from until nodes.size) {
            val html = nodes[i] as? HtmlBlock ?: continue
            level += DetailsHtml.countOpens(html.literal) - DetailsHtml.countCloses(html.literal)
            if (level <= 0) return i
        }
        return -1
    }

    private fun rangeOf(node: Node): SourceRange {
        var start = Int.MAX_VALUE
        var end = -1
        for (span in node.sourceSpans) {
            start = minOf(start, span.inputIndex)
            end = maxOf(end, span.inputIndex + span.length)
        }
        if (end < 0) {
            val parent = node.parent
            return if (parent != null && parent.sourceSpans.isNotEmpty()) rangeOf(parent) else SourceRange(0, 0)
        }
        val s = start.coerceIn(0, sourceLength)
        return SourceRange(s, end.coerceIn(s, sourceLength))
    }
}

/** String-level handling of `<details>` HTML blocks (DOC-09). */
internal object DetailsHtml {
    private val openRegex = Regex("""^\s*<details(\s[^>]*)?>""", RegexOption.IGNORE_CASE)
    private val openAttrRegex = Regex("""(^|\s)open(\s|=|$)""", RegexOption.IGNORE_CASE)
    private val summaryRegex =
        Regex("""^\s*<summary[^>]*>(.*?)</summary>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private const val CLOSE = "</details>"

    class Head(
        val open: Boolean,
        val summaryMarkdown: String?,
        val bodyBeforeClose: String,
        val closed: Boolean,
        val after: String,
    )

    class Split(
        val before: String,
        val after: String,
    )

    fun opensDetails(literal: String): Boolean = openRegex.containsMatchIn(literal)

    fun countOpens(literal: String): Int =
        Regex("<details(\\s[^>]*)?>", RegexOption.IGNORE_CASE).findAll(literal).count()

    fun countCloses(literal: String): Int = Regex(CLOSE, RegexOption.IGNORE_CASE).findAll(literal).count()

    fun splitAtClose(literal: String): Split {
        val i = literal.indexOf(CLOSE, ignoreCase = true)
        return if (i < 0) Split(literal, "") else Split(literal.substring(0, i), literal.substring(i + CLOSE.length))
    }

    fun parseOpening(literal: String): Head {
        val m = openRegex.find(literal) ?: return Head(false, null, literal, false, "")
        val open = openAttrRegex.containsMatchIn(m.groupValues[1])
        var rest = literal.substring(m.range.last + 1)
        var summary: String? = null
        val s = summaryRegex.find(rest)
        if (s != null) {
            summary = s.groupValues[1].trim()
            rest = rest.substring(s.range.last + 1)
        }
        val split = splitAtClose(rest)
        val closed = rest.contains(CLOSE, ignoreCase = true)
        return Head(open, summary, if (closed) split.before else rest, closed, split.after)
    }
}

/** GFM table node to [Table]; short body rows are padded and long ones truncated to the header width (SYN-06). */
internal object TableConverter {
    fun convert(
        table: TableBlock,
        range: SourceRange,
    ): Table {
        var header: List<CmTableCell> = emptyList()
        val bodyRows = ArrayList<List<CmTableCell>>()
        for (section in children(table)) {
            val rows =
                children(section).filterIsInstance<CmTableRow>().map { row ->
                    children(row).filterIsInstance<CmTableCell>()
                }
            when (section) {
                is TableHead -> header = rows.firstOrNull() ?: emptyList()
                is TableBody -> bodyRows.addAll(rows)
            }
        }
        val alignments = header.map { alignmentOf(it) }
        val columns = alignments.size
        val rows =
            bodyRows.map { cells ->
                TableRow(
                    List(columns) { c ->
                        cells.getOrNull(c)?.let { TableCell(InlineConverter.convert(it)) }
                            ?: TableCell(emptyList())
                    },
                )
            }
        return Table(alignments, TableRow(header.map { TableCell(InlineConverter.convert(it)) }), rows, range)
    }

    private fun alignmentOf(cell: CmTableCell): ColumnAlignment =
        when (cell.alignment) {
            CmTableCell.Alignment.LEFT -> ColumnAlignment.LEFT
            CmTableCell.Alignment.CENTER -> ColumnAlignment.CENTER
            CmTableCell.Alignment.RIGHT -> ColumnAlignment.RIGHT
            null -> ColumnAlignment.NONE
        }
}

private fun languageOf(info: String?): String? {
    val word =
        info
            ?.trim()
            ?.substringBefore(' ')
            ?.substringBefore('\t')
            ?.lowercase()
    return if (word.isNullOrEmpty()) null else word
}

private fun trimOneNewline(literal: String): String = literal.removeSuffix("\n")
