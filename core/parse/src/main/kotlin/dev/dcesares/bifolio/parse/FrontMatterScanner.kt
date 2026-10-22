package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.model.SourceRange

/**
 * Detects a leading YAML front matter block (SYN-13) in line-ending-normalized text. Flat `key: value` pairs and
 * simple lists are read; other nested YAML lines are ignored but stay in [FrontMatter.raw]. No dependency (ADR-0003).
 */
internal object FrontMatterScanner {
    private const val FENCE = "---"
    private const val ALT_CLOSE = "..."
    private const val MIN_QUOTED = 2
    private const val LIST_SEPARATOR = ", "
    private val PLAIN_KEYS = setOf("title", "lang", "language")

    /** The front matter at the very start of [source], or null when absent or unclosed. */
    fun scan(source: String): FrontMatter? {
        val firstEnd = lineEnd(source, 0)
        if (source.substring(0, firstEnd).trimEnd() != FENCE) return null
        var pos = firstEnd + 1
        while (pos <= source.length) {
            val end = lineEnd(source, pos)
            val line = source.substring(pos, end).trimEnd()
            if (line == FENCE || line == ALT_CLOSE) return build(source, firstEnd + 1, pos, end)
            if (end >= source.length) return null
            pos = end + 1
        }
        return null
    }

    /** [source] with the characters of [block] blanked to spaces (newlines kept), so offsets stay valid. */
    fun blank(
        source: String,
        block: FrontMatter,
    ): String {
        val sb = StringBuilder(source)
        for (i in block.range.start until block.range.end) if (sb[i] != '\n') sb.setCharAt(i, ' ')
        return sb.toString()
    }

    private fun build(
        source: String,
        bodyStart: Int,
        bodyEnd: Int,
        blockEnd: Int,
    ): FrontMatter {
        val entries = parseEntries(source.substring(bodyStart, bodyEnd))
        return FrontMatter(
            raw = source.substring(0, blockEnd),
            title = find(entries, "title"),
            language = find(entries, "lang", "language"),
            range = SourceRange(0, blockEnd),
            entries = entries,
        )
    }

    private fun find(
        entries: List<Pair<String, String>>,
        vararg keys: String,
    ): String? = entries.firstOrNull { it.first.lowercase() in keys }?.second?.ifBlank { null }

    /**
     * Flat `key: value` lines. A key with no value followed by `- item` lines takes the items as a comma list, and
     * an inline `[a, b]` list reads the same way (tags, authors). Other nested YAML is ignored.
     */
    private fun parseEntries(body: String): List<Pair<String, String>> {
        val lines = body.lines()
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < lines.size) {
            val entry = parseLine(lines[i++])
            if (entry != null) {
                val items = if (entry.second.isEmpty()) itemsFrom(lines, i) else emptyList()
                i += items.size
                val value = if (items.isEmpty()) flowList(entry) else items.joinToString(LIST_SEPARATOR)
                out += entry.first to value
            }
        }
        return out
    }

    /** The unquoted `- item` lines that start at [from], one entry per line. */
    private fun itemsFrom(
        lines: List<String>,
        from: Int,
    ): List<String> =
        lines
            .subList(from, lines.size)
            .takeWhile { line -> line.trimStart().let { it.startsWith("- ") || it == "-" } }
            .map { unquote(it.trim().drop(1).trim()) }

    /** `[a, "b"]` as `a, b`; scalars, and the title and language values, are left as written. */
    private fun flowList(entry: Pair<String, String>): String {
        val (key, value) = entry
        if (key.lowercase() in PLAIN_KEYS || !value.startsWith('[') || !value.endsWith(']')) return value
        return value
            .substring(1, value.length - 1)
            .split(',')
            .map { unquote(it.trim()) }
            .filter { it.isNotEmpty() }
            .joinToString(LIST_SEPARATOR)
    }

    private fun parseLine(line: String): Pair<String, String>? {
        if (line.isEmpty() || line[0].isWhitespace() || line[0] == '#') return null
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val key = line.substring(0, colon).trim()
        return if (key.isEmpty()) null else key to unquote(line.substring(colon + 1).trim())
    }

    private fun unquote(value: String): String {
        if (value.length < MIN_QUOTED) return value
        val q = value[0]
        if ((q != '"' && q != '\'') || value.last() != q) return value
        val inner = value.substring(1, value.length - 1)
        return if (q == '"') inner.replace("\\\"", "\"").replace("\\\\", "\\") else inner.replace("''", "'")
    }

    private fun lineEnd(
        s: String,
        from: Int,
    ): Int {
        val i = s.indexOf('\n', from)
        return if (i < 0) s.length else i
    }
}
