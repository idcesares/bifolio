package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownLoader
import dev.dcesares.bifolio.parse.MarkdownParser
import io.kotest.property.RandomSource
import java.io.File

/** Reads `testdata/corpus` (docs/TESTING.md section 3). Gradle runs tests in the module directory. */
internal object CorpusFiles {
    private val dir: File =
        File("../../testdata/corpus").also {
            check(it.isDirectory) { "corpus not found at ${it.absolutePath}" }
        }

    fun names(): List<String> = dir.listFiles { f -> f.extension == "md" }!!.map { it.name }.sorted()

    fun load(name: String): Document {
        val loaded = MarkdownLoader.load(File(dir, name).readBytes())
        return (loaded as dev.dcesares.bifolio.parse.LoadResult.Loaded).document
    }
}

/** Seeded Markdown generator: every kind of block the paginator handles, with random lengths. */
internal class RandomMarkdown(
    seed: Long,
) {
    private val rs = RandomSource.seeded(seed)
    private val random = rs.random

    private fun int(range: IntRange): Int = random.nextInt(range.first, range.last + 1)

    private fun word(): String = String(CharArray(int(1..11)) { 'a' + random.nextInt(26) })

    private fun words(count: Int): String = List(count) { word() }.joinToString(" ")

    private fun paragraph(): String = words(int(1..90))

    private fun heading(): String = "#".repeat(int(1..6)) + " " + words(int(1..8))

    private fun list(): String {
        val ordered = random.nextBoolean()
        val loose = random.nextInt(4) == 0
        val items =
            List(int(1..9)) { i ->
                val marker = if (ordered) "${i + 1}." else "-"
                val body = "$marker ${words(int(1..40))}"
                if (random.nextInt(5) == 0) "$body\n   - ${words(int(1..12))}" else body
            }
        return items.joinToString(if (loose) "\n\n" else "\n")
    }

    private fun quote(): String = List(int(1..2)) { "> " + words(int(1..70)) }.joinToString("\n>\n")

    private fun code(): String =
        "```\n" + List(int(1..60)) { " ".repeat(int(0..4)) + words(int(0..8)) }.joinToString("\n") + "\n```"

    private fun table(): String {
        val cols = int(2..5)
        val header = (1..cols).joinToString(" | ", "| ", " |") { word() }
        val delimiter = (1..cols).joinToString(" | ", "| ", " |") { "---" }
        val rows = List(int(0..40)) { (1..cols).joinToString(" | ", "| ", " |") { words(int(1..6)) } }
        return (listOf(header, delimiter) + rows).joinToString("\n")
    }

    fun block(): String =
        when (random.nextInt(WEIGHT_TOTAL)) {
            in 0..11 -> paragraph()
            in 12..17 -> heading()
            in 18..20 -> list()
            21, 22 -> quote()
            23 -> code()
            24 -> table()
            else -> "---"
        }

    /** A document of [blockCount] blocks; at most three headings in a row, so keep-with-next stays feasible. */
    fun document(blockCount: Int): Document {
        val parts = ArrayList<String>(blockCount)
        var run = 0
        repeat(blockCount) {
            var b = block()
            val isHeading = b.startsWith("#")
            if (isHeading && run >= MAX_HEADING_RUN) b = paragraph()
            run = if (b.startsWith("#")) run + 1 else 0
            parts.add(b)
        }
        return MarkdownParser.parse(parts.joinToString("\n\n") + "\n")
    }

    /** About [wordCount] words in paragraphs of 20 to 120 words, a heading every ~10 paragraphs (C-02). */
    fun prose(wordCount: Int): Document {
        val sb = StringBuilder()
        var written = 0
        var n = 0
        while (written < wordCount) {
            if (n % HEADING_EVERY == 0) sb.append("## ").append(words(int(1..6))).append("\n\n")
            val len = int(20..120)
            sb.append(words(len)).append("\n\n")
            written += len
            n++
        }
        return MarkdownParser.parse(sb.toString())
    }

    private companion object {
        const val WEIGHT_TOTAL = 26
        const val MAX_HEADING_RUN = 3
        const val HEADING_EVERY = 10
    }
}
