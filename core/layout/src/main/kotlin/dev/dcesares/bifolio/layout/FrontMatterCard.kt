package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.FrontMatter

/**
 * What the front matter card shows and how tall it is (SYN-13, PAG-07). The paginator measures it here and the
 * reader draws the same title and rows with the same roles and width, so both break lines alike (ADR-0013).
 *
 * The title is the `title` entry. The rows are the other flat `key: value` entries, minus `lang`, which only
 * sets the hyphenation locale. When that leaves nothing, the `lang` row is shown. A block with no `key: value`
 * entry at all shows its YAML source lines.
 */
public object FrontMatterCard {
    /** Entries shown below the title; the rest stay in the file. */
    public const val MAX_ROWS: Int = 8

    /** Longest value shown, in characters, before an ellipsis. */
    public const val MAX_VALUE_CHARS: Int = 200

    public val TITLE_ROLE: TextRole = TextRole.HEADING_4
    public val ROWS_ROLE: TextRole = TextRole.TABLE_CELL

    private const val ELLIPSIS = "…"
    private const val TITLE_KEY = "title"
    private val HIDDEN_KEYS = setOf(TITLE_KEY, "lang", "language")

    public fun title(block: FrontMatter): String? = block.title?.takeIf { it.isNotBlank() }

    /** The rows as lines of `key: value`, joined by `\n`; empty only when the block has no content at all. */
    public fun rows(block: FrontMatter): String {
        val shown = block.entries.filter { it.first.lowercase() !in HIDDEN_KEYS }
        val entries = shown.ifEmpty { block.entries.filter { it.first.lowercase() != TITLE_KEY } }
        val lines =
            if (block.entries.isEmpty()) {
                sourceLines(block)
            } else {
                entries.map { (key, value) -> "$key: ${clip(value)}".trimEnd() }
            }
        return lines.take(MAX_ROWS).joinToString("\n")
    }

    /**
     * Height in px of the card at [width]: [padding] around and between the title and the rows, whatever lines
     * [measurer] breaks them into. At least 1, so an empty card is still one line of the layout.
     */
    public fun height(
        block: FrontMatter,
        width: Int,
        padding: Int,
        measurer: BlockMeasurer,
    ): Int {
        val inner = maxOf(width - 2 * padding, 1)
        val title = title(block)?.let { measurer.measureLines(it, TITLE_ROLE, inner).height } ?: 0
        val rows = rows(block).let { if (it.isEmpty()) 0 else measurer.measureLines(it, ROWS_ROLE, inner).height }
        val gap = if (title > 0 && rows > 0) padding else 0
        return maxOf(1, 2 * padding + title + gap + rows)
    }

    private fun clip(value: String): String =
        if (value.length <= MAX_VALUE_CHARS) value else value.take(MAX_VALUE_CHARS - 1).trimEnd() + ELLIPSIS

    /** The lines between the fences, trailing space dropped, blank lines skipped. */
    private fun sourceLines(block: FrontMatter): List<String> =
        block.raw
            .lines()
            .drop(1)
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() && it != "---" && it != "..." }
            .map(::clip)
}
