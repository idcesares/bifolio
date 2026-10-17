package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.InlineStyle
import dev.dcesares.bifolio.model.plainText
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text

/** One recognized inline HTML tag of the DOC-09 safe subset. */
internal sealed interface HtmlTag {
    data object LineBreak : HtmlTag

    data class Open(
        val style: InlineStyle,
    ) : HtmlTag

    data class Close(
        val style: InlineStyle,
    ) : HtmlTag
}

internal object HtmlSubset {
    private val tagRegex =
        Regex("""^<(?<close>/?)(?<name>sub|sup|kbd|mark|u|br)\s*(?<self>/?)>$""", RegexOption.IGNORE_CASE)

    /** Parses [literal] as one of `<br>`, `<sub>`, `<sup>`, `<kbd>`, `<mark>`, `<u>` or its closing tag, else null. */
    fun parseTag(literal: String): HtmlTag? {
        val m = tagRegex.matchEntire(literal.trim()) ?: return null
        val closing = m.groups["close"]!!.value.isNotEmpty()
        val selfClosing = m.groups["self"]!!.value.isNotEmpty()
        val name = m.groups["name"]!!.value.lowercase()
        return when {
            name == "br" -> if (closing) null else HtmlTag.LineBreak
            selfClosing -> null
            else -> {
                val style = styleOf(name)
                if (closing) HtmlTag.Close(style) else HtmlTag.Open(style)
            }
        }
    }

    private fun styleOf(name: String): InlineStyle =
        when (name) {
            "sub" -> InlineStyle.SUBSCRIPT
            "sup" -> InlineStyle.SUPERSCRIPT
            "kbd" -> InlineStyle.KEYBOARD
            "mark" -> InlineStyle.MARK
            else -> InlineStyle.UNDERLINE
        }
}

/** commonmark inline nodes to [Inline]. Unsupported HTML becomes text (DOC-09). Stateless. */
internal object InlineConverter {
    fun convert(parent: Node): List<Inline> {
        val nodes = children(parent)
        return sequence(nodes, 0, nodes.size)
    }

    /** Plain text with `\n` turned into soft breaks. */
    fun plainLines(text: String): List<Inline> {
        val out = ArrayList<Inline>()
        val lines = text.split('\n')
        for ((i, line) in lines.withIndex()) {
            if (i > 0) out.add(Inline.SoftBreak)
            if (line.isNotEmpty()) out.add(Inline.Text(line))
        }
        return out
    }

    private fun sequence(
        nodes: List<Node>,
        from: Int,
        to: Int,
    ): List<Inline> {
        val out = ArrayList<Inline>()
        var i = from
        while (i < to) {
            val node = nodes[i]
            i =
                if (node is HtmlInline) {
                    htmlInline(nodes, i, to, out)
                } else {
                    convertNode(node, out)
                    i + 1
                }
        }
        return out
    }

    /** Handles the HTML node at [index]; returns the index of the next node to process. */
    private fun htmlInline(
        nodes: List<Node>,
        index: Int,
        to: Int,
        out: MutableList<Inline>,
    ): Int {
        val literal = (nodes[index] as HtmlInline).literal
        when (val tag = HtmlSubset.parseTag(literal)) {
            HtmlTag.LineBreak -> out.add(Inline.HardBreak)
            is HtmlTag.Open -> {
                val close = findClose(nodes, index + 1, to, tag.style)
                if (close < 0) {
                    addText(out, literal)
                } else {
                    out.add(Inline.Styled(tag.style, sequence(nodes, index + 1, close)))
                    return close + 1
                }
            }
            is HtmlTag.Close, null -> addText(out, literal)
        }
        return index + 1
    }

    private fun findClose(
        nodes: List<Node>,
        from: Int,
        to: Int,
        style: InlineStyle,
    ): Int {
        var depth = 0
        for (i in from until to) {
            val tag = (nodes[i] as? HtmlInline)?.let { HtmlSubset.parseTag(it.literal) }
            if (tag is HtmlTag.Open && tag.style == style) {
                depth++
            } else if (tag is HtmlTag.Close && tag.style == style) {
                if (depth == 0) return i
                depth--
            }
        }
        return -1
    }

    private fun convertNode(
        node: Node,
        out: MutableList<Inline>,
    ) {
        when (node) {
            is Text -> addText(out, node.literal)
            is Emphasis -> out.add(Inline.Emphasis(convert(node)))
            is StrongEmphasis -> out.add(Inline.Strong(convert(node)))
            is Strikethrough -> out.add(Inline.Strikethrough(convert(node)))
            is Code -> out.add(Inline.Code(node.literal))
            is Link -> out.add(Inline.Link(node.destination, node.title, convert(node)))
            is Image -> out.add(Inline.Image(node.destination, node.title, plainText(convert(node))))
            is SoftLineBreak -> out.add(Inline.SoftBreak)
            is HardLineBreak -> out.add(Inline.HardBreak)
            is TaskListItemMarker -> Unit
            else -> out.addAll(convert(node))
        }
    }

    private fun addText(
        out: MutableList<Inline>,
        text: String,
    ) {
        if (text.isEmpty()) return
        val last = out.lastOrNull()
        if (last is Inline.Text) out[out.size - 1] = Inline.Text(last.text + text) else out.add(Inline.Text(text))
    }
}

internal fun children(parent: Node): List<Node> {
    val list = ArrayList<Node>()
    var c = parent.firstChild
    while (c != null) {
        list.add(c)
        c = c.next
    }
    return list
}
