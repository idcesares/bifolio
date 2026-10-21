package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Normalizer
import kotlin.random.Random

/** UI-08: search over the source text, matches mapped to anchors (ADR-0005). */
class DocumentSearchTest {
    private fun search(
        md: String,
        query: String,
        limit: Int = DocumentSearch.DEFAULT_LIMIT,
    ): Pair<Document, SearchResult> {
        val doc = MarkdownParser.parse(md)
        return doc to DocumentSearch.search(doc, query, limit)
    }

    private fun sourceAt(
        doc: Document,
        m: SearchMatch,
    ): String {
        val start = doc.sourceOffsetOf(m.anchor)
        return doc.source.substring(start, start + m.sourceLength)
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    @Test
    fun `SYN-12 text before and after inline and display math is found with anchors into the source`() {
        val md =
            "Alpha \$x^2\$ omega and \$\$\\frac{1}{2}\$\$ zeta.\n\n\$\$\ny = mc\n\$\$\n\n" +
                "final omega\n\n```math\nomega\n```\n"
        val (doc, r) = search(md, "omega")
        assertEquals(listOf("omega", "omega"), r.matches.map { sourceAt(doc, it) })
        assertEquals(listOf(0, 2), r.matches.map { it.blockIndex })
        val (d2, r2) = search(md, "alpha")
        assertEquals("Alpha", sourceAt(d2, r2.matches.single()))
        val (d3, r3) = search(md, "zeta")
        assertEquals("zeta", sourceAt(d3, r3.matches.single()))
    }

    @Test
    fun `empty and blank queries return nothing`() {
        for (q in listOf("", " ", "\t\n  ", "́")) {
            val (_, r) = search("alpha beta\n", q)
            assertTrue(r.matches.isEmpty(), "query '$q'")
            assertFalse(r.truncated)
        }
    }

    @Test
    fun `case and diacritics are ignored in both directions`() {
        val md = "Coração e CORAÇÕES, acao, ação.\n"
        val (doc, r) = search(md, "coracao")
        assertEquals(1, r.matches.size)
        assertEquals("Coração", sourceAt(doc, r.matches[0]))
        val (doc2, r2) = search(md, "AÇÃO")
        assertEquals(listOf("ação", "acao", "ação"), r2.matches.map { sourceAt(doc2, it) })
    }

    @Test
    fun `anchor points into the original source and length covers the original span`() {
        val (d2, r2) = search("# Título\n\nUm *São* João e `código`.\n", "são")
        assertEquals(1, r2.matches.size)
        assertEquals("São", sourceAt(d2, r2.matches[0]))
        val (d3, r3) = search("# Título\n\nUm *São* João e `código`.\n", "codigo")
        assertEquals("código", sourceAt(d3, r3.matches.single()))
        assertEquals(
            Anchor(0, 2),
            search("# Título\n", "tit")
                .second.matches
                .single()
                .anchor,
        )
    }

    @Test
    fun `decomposed accents in the text are covered by the match`() {
        val (_, r) = search("café au lait\n", "cafe")
        assertEquals(5, r.matches.single().length)
    }

    @Test
    fun `soft breaks match as spaces and matches never span blocks`() {
        val (_, r) = search("alpha\nbeta\n\ngamma\n", "alpha beta")
        assertEquals(1, r.matches.size)
        assertTrue(search("alpha\n\nbeta\n", "alpha beta").second.matches.isEmpty())
    }

    @Test
    fun `every block kind is searched with the top level block index`() {
        val md =
            """
            # needle head

            para needle

            - item needle
              - nested needle

            > quote needle

            > [!NOTE]
            > callout needle

            | a | b |
            |---|---|
            | cell needle | x |

            ```
            code needle
            ```

            text[^1]

            [^1]: foot needle
            """.trimIndent() + "\n"
        val (doc, r) = search(md, "needle")
        assertEquals(9, r.matches.size, r.matches.toString())
        assertEquals(r.matches.map { it.blockIndex }.sorted(), r.matches.map { it.blockIndex })
        for (m in r.matches) {
            assertEquals(m.blockIndex, m.anchor.blockIndex)
            val block = doc.blocks[m.blockIndex]
            val start = doc.sourceOffsetOf(m.anchor)
            assertTrue(start >= block.range.start && start + m.sourceLength <= block.range.end)
            assertEquals("needle", sourceAt(doc, m).lowercase(), m.toString())
        }
    }

    @Test
    fun `front matter is not searched`() {
        val (_, r) = search("---\ntitle: needle\n---\n\nbody\n", "needle")
        assertTrue(r.matches.isEmpty())
    }

    @Test
    fun `results are capped and flagged`() {
        val (_, r) = search("a ".repeat(50) + "\n", "a", limit = 10)
        assertEquals(10, r.matches.size)
        assertTrue(r.truncated)
        val (_, exact) = search("a ".repeat(10) + "\n", "a", limit = 10)
        assertFalse(exact.truncated)
    }

    @Test
    fun `snippet is trimmed on word boundaries with ellipses and no newlines`() {
        val left = "lorem ipsum dolor sit amet consectetur adipiscing elit sed do"
        val right = "eiusmod tempor incididunt ut labore et dolore magna aliqua"
        val (_, r) = search("$left\nTARGET $right\n", "target")
        val s = r.matches.single().snippet
        assertTrue(s.text.startsWith("…") && s.text.endsWith("…"))
        assertFalse(s.text.contains('\n'))
        assertEquals("TARGET", s.text.substring(s.matchStart, s.matchEnd))
        assertTrue(s.matchStart <= 42 && s.text.length - s.matchEnd <= 42)
        assertFalse(s.text.drop(1).startsWith(" ") || s.text.startsWith("…") && s.text[1].isWhitespace())
        val (_, short) = search("short target text\n", "target")
        assertEquals(
            "short target text",
            short.matches
                .single()
                .snippet.text,
        )
    }

    @Test
    fun `cancellation propagates`() {
        val doc = MarkdownParser.parse("a\n\nb\n\nc\n")
        assertThrows(IllegalStateException::class.java) {
            DocumentSearch.search(doc, "a") { error("cancelled") }
        }
    }

    @Test
    fun `property every match equals the query modulo case and diacritics`() {
        val words = listOf("São", "joão", "CORAÇÃO", "coracao", "ação", "Élan", "elan", "über", "uber", "naïve", "x")
        val rnd = Random(42)
        repeat(200) { n ->
            val md =
                buildString {
                    repeat(rnd.nextInt(1, 6)) {
                        val prefix = listOf("", "# ", "> ", "- ", "1. ")[rnd.nextInt(5)]
                        append(prefix)
                        repeat(rnd.nextInt(1, 12)) { append(words[rnd.nextInt(words.size)]).append(' ') }
                        append("\n\n")
                    }
                }
            val doc = MarkdownParser.parse(md)
            val query = words[rnd.nextInt(words.size)].let { if (rnd.nextBoolean()) it.uppercase() else it }
            val r = DocumentSearch.search(doc, query)
            val expected = Regex(Regex.escape(fold(query))).findAll(fold(md.replace("\n", " "))).count()
            for (m in r.matches) {
                assertEquals(fold(query), fold(sourceAt(doc, m)), "case $n query $query anchor ${m.anchor}")
                assertEquals(m.blockIndex, m.anchor.blockIndex)
            }
            assertEquals(expected, r.matches.size, "case $n query $query")
        }
    }
}
