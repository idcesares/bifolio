package dev.dcesares.bifolio.model

/** Column alignment of a GFM table (SYN-06). */
public enum class ColumnAlignment { NONE, LEFT, CENTER, RIGHT }

/** State of a task list box (SYN-03). Read-only glyph, never a toggle. */
public enum class TaskState { UNCHECKED, CHECKED }

/** GitHub alert kinds (SYN-10). Reserved for M5. */
public enum class CalloutKind { NOTE, TIP, IMPORTANT, WARNING, CAUTION }

/**
 * One renderable unit. Top-level blocks of [Document.blocks] are addressed by block index.
 * [range] maps the block to its characters in [Document.source] (DOC-07).
 */
public sealed interface Block {
    public val range: SourceRange
}

public data class Heading(
    val level: Int,
    val content: List<Inline>,
    /** GitHub-style anchor id, unique in the document (SYN-01). */
    val slug: String,
    override val range: SourceRange,
) : Block {
    init {
        require(level in MIN_LEVEL..MAX_LEVEL) { "heading level must be 1..6: $level" }
    }
}

private const val MIN_LEVEL = 1
private const val MAX_LEVEL = 6

public data class Paragraph(
    val content: List<Inline>,
    override val range: SourceRange,
) : Block

public data class ListItem(
    val task: TaskState?,
    val blocks: List<Block>,
    val range: SourceRange,
)

public data class ListBlock(
    val ordered: Boolean,
    /** Number of the first item for ordered lists; 1 for bullet lists. */
    val startNumber: Int,
    val tight: Boolean,
    val items: List<ListItem>,
    override val range: SourceRange,
) : Block

public data class BlockQuote(
    val blocks: List<Block>,
    override val range: SourceRange,
) : Block

public data class CodeBlock(
    /** Info string language, lowercase, or null when absent. */
    val language: String?,
    /** Code without the trailing line terminator. */
    val code: String,
    val fenced: Boolean,
    override val range: SourceRange,
) : Block

public data class TableCell(
    val content: List<Inline>,
)

public data class TableRow(
    val cells: List<TableCell>,
)

public data class Table(
    val alignments: List<ColumnAlignment>,
    val header: TableRow,
    val rows: List<TableRow>,
    override val range: SourceRange,
) : Block {
    init {
        require(header.cells.size == alignments.size) { "header cells must match alignments" }
    }

    public val columnCount: Int get() = alignments.size
}

public data class ThematicBreak(
    override val range: SourceRange,
) : Block

/** `<details>/<summary>` collapsible block (DOC-09). */
public data class HtmlDetails(
    val summary: List<Inline>,
    val open: Boolean,
    val blocks: List<Block>,
    override val range: SourceRange,
) : Block

/** Reserved for M5 (SYN-08). Atomic. */
public data class ImageBlock(
    val source: String,
    val title: String?,
    val alt: String,
    override val range: SourceRange,
) : Block

/** Reserved for M5 (SYN-09). */
public data class FootnoteDefinition(
    val label: String,
    val blocks: List<Block>,
    override val range: SourceRange,
) : Block

/** Reserved for M5 (SYN-10). */
public data class Callout(
    val kind: CalloutKind,
    val blocks: List<Block>,
    override val range: SourceRange,
) : Block

/** Reserved for M5 (SYN-12). Atomic display math. */
public data class MathBlock(
    val tex: String,
    override val range: SourceRange,
) : Block

/** SYN-13. Atomic metadata card; [entries] are the flat `key: value` pairs in source order, unknown keys kept. */
public data class FrontMatter(
    val raw: String,
    val title: String?,
    val language: String?,
    override val range: SourceRange,
    val entries: List<Pair<String, String>> = emptyList(),
) : Block

/** Reserved for M5 (SYN-14). Source shown as code with a "Diagram (not rendered)" label. */
public data class MermaidFence(
    val source: String,
    override val range: SourceRange,
) : Block

private fun childGroups(block: Block): List<List<Block>> =
    when (block) {
        is BlockQuote -> listOf(block.blocks)
        is ListBlock -> block.items.map { it.blocks }
        is HtmlDetails -> listOf(block.blocks)
        is Callout -> listOf(block.blocks)
        is FootnoteDefinition -> listOf(block.blocks)
        else -> emptyList()
    }

/** Maximum container nesting (lists, quotes, details) found in [blocks]; 0 when flat (DOC-08). */
public fun nestingDepth(blocks: List<Block>): Int {
    var max = 0
    for (block in blocks) {
        val groups = childGroups(block)
        if (groups.isEmpty()) continue
        val inner = groups.maxOf { nestingDepth(it) }
        if (inner + 1 > max) max = inner + 1
    }
    return max
}
