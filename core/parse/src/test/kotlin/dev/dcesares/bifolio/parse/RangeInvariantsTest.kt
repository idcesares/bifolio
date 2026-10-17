package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.nestingDepth
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** DOC-07 and DOC-08 invariants on the whole corpus and on seeded random documents. */
class RangeInvariantsTest {
    private fun check(
        doc: Document,
        label: String,
    ) {
        val len = doc.source.length
        assertSiblings(doc.blocks, 0, len, label)
        assertTrue(nestingDepth(doc.blocks) <= 32, "$label: nesting ${nestingDepth(doc.blocks)}")
    }

    private fun assertSiblings(
        blocks: List<Block>,
        from: Int,
        to: Int,
        label: String,
    ) {
        var cursor = from
        for (b in blocks) {
            val r = b.range
            assertTrue(r.start >= cursor, "$label: block $b starts at ${r.start} before previous end $cursor")
            assertTrue(r.end <= to, "$label: block $b ends at ${r.end} past parent end $to")
            cursor = r.end
            if (b is ListBlock) {
                var itemCursor = r.start
                for (item in b.items) {
                    assertTrue(item.range.start >= itemCursor && item.range.end <= r.end, "$label: item range")
                    itemCursor = item.range.end
                    assertSiblings(item.blocks, item.range.start, item.range.end, label)
                }
            } else {
                for (group in childGroups(b)) assertSiblings(group, r.start, r.end, label)
            }
        }
    }

    @Test
    fun `DOC-07 corpus documents have ordered, contained, in-bounds ranges`() {
        for (name in Corpus.names()) check(Corpus.load(name), name)
    }

    @Test
    fun `DOC-07 seeded random documents keep ranges valid and never throw`() {
        val fragments =
            listOf(
                "# Heading",
                "## Another *one*",
                "Paragraph text with **bold** and `code`.",
                "> quote",
                "> > deeper",
                "- item",
                "  - nested",
                "1. one",
                "2. two",
                "- [ ] todo",
                "- [x] done",
                "```kotlin\nval x = 1\n```",
                "    indented",
                "| a | b |\n|---|:-:|\n| 1 | 2 |",
                "---",
                "<details>\n<summary>S</summary>\n\nBody\n\n</details>",
                "<div>raw</div>",
                "text<br>more <kbd>K</kbd>",
                "[l](http://x.y)",
                "",
                "Setext\n======",
                "日本語 ção",
                "![img](a.png)",
                "[^1]: note",
                "$$ x $$",
                "***",
                "<u>open only",
            )
        val rs = RandomSource.seeded(20260416L)
        val gen = Arb.list(Arb.element(fragments), 0..40)
        repeat(300) { i ->
            val md = gen.next(rs).joinToString(if (Arb.int(0..1).next(rs) == 0) "\n" else "\n\n")
            check(MarkdownParser.parse(md), "seed doc $i")
        }
    }
}
