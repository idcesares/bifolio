package dev.dcesares.bifolio.highlight

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class CoverageInvariantTest {
    private val h: CodeHighlighter = RuleBasedHighlighter()
    private val languages =
        listOf(
            null,
            "kotlin",
            "java",
            "python",
            "javascript",
            "typescript",
            "json",
            "yaml",
            "toml",
            "bash",
            "sql",
            "html",
            "css",
            "markdown",
            "c",
            "cpp",
            "go",
            "rust",
            "swift",
            "diff",
            "nope",
        )
    private val fragments =
        listOf(
            "\"",
            "'",
            "`",
            "\"\"\"",
            "/*",
            "*/",
            "//",
            "#",
            "--",
            "<!--",
            "-->",
            "<",
            ">",
            "</",
            "=",
            ":",
            "\\",
            "\n",
            " ",
            "\t",
            "fun",
            "if",
            "SELECT",
            "1",
            "1.5",
            "0x",
            "$",
            "(",
            ")",
            "{",
            "}",
            "@@",
            "+",
            "- ",
            "é",
            "😀",
            "a",
            "Foo",
            "```",
            "> ",
            "**",
            "\r\n",
        )

    private fun randomSource(
        rs: RandomSource,
        length: Int,
    ): String {
        val sb = StringBuilder()
        repeat(length) { sb.append(fragments[Arb.int(0, fragments.size - 1).next(rs)]) }
        return sb.toString()
    }

    private fun assertCovers(
        src: String,
        lang: String?,
    ) {
        val spans = h.tokenize(src, lang)
        var pos = 0
        for (s in spans) {
            assertEquals(pos, s.start, "gap or overlap in $lang")
            assertTrue(s.end > s.start && s.end <= src.length, "bad span $s in $lang")
            pos = s.end
        }
        assertEquals(src.length, pos, "not fully covered in $lang")
    }

    @Test
    fun spansAreSortedDisjointInBoundsAndCoverTheSource() {
        val rs = RandomSource.seeded(11)
        repeat(3000) { i ->
            val src = randomSource(rs, Arb.int(0, 40).next(rs))
            assertCovers(src, languages[i % languages.size])
        }
    }

    @Test
    fun everyPrefixOfARealSnippetCovers() {
        val snippet = "fun f(x: Int) = \"a\\\"b\" /* c */ // d\n<a href='x'>#h</a> SELECT 1.5e3 \"\"\"z"
        for (lang in languages) for (end in 0..snippet.length) assertCovers(snippet.substring(0, end), lang)
    }

    @Test
    fun pathologicalInputsStayLinear() {
        val n = 1_000_000
        val inputs =
            listOf(
                "a".repeat(n),
                "\"" + "a".repeat(n),
                "/*" + "*".repeat(n),
                "`".repeat(n),
                "\"\"\"" + "\\".repeat(n),
                "<a " + "b=\"".repeat(n / 3),
                "1.".repeat(n / 2),
                " ".repeat(n) + "k" + " ".repeat(n) + ":",
                "k: ".repeat(n / 3),
                "<".repeat(n),
                "+".repeat(n),
                "-\n".repeat(n / 2),
                "a ".repeat(n / 2),
            )
        for (lang in languages) {
            for (src in inputs) {
                assertTimeoutPreemptively(Duration.ofSeconds(10)) { assertCovers(src, lang) }
            }
        }
    }
}
