package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ListBlock
import java.io.File

/** Reads files of `testdata/corpus` (docs/TESTING.md section 3). Gradle runs tests in the module directory. */
internal object Corpus {
    private val dir: File =
        File("../../testdata/corpus").also {
            check(it.isDirectory) { "corpus not found at ${it.absolutePath}" }
        }

    fun bytes(name: String): ByteArray = File(dir, name).readBytes()

    fun names(): List<String> = dir.listFiles { f -> f.extension == "md" }!!.map { it.name }.sorted()

    fun load(name: String): Document = (MarkdownLoader.load(bytes(name)) as LoadResult.Loaded).document
}

/** Child block groups of a container block. */
internal fun childGroups(block: Block): List<List<Block>> =
    when (block) {
        is BlockQuote -> listOf(block.blocks)
        is ListBlock -> block.items.map { it.blocks }
        is HtmlDetails -> listOf(block.blocks)
        is Callout -> listOf(block.blocks)
        is FootnoteDefinition -> listOf(block.blocks)
        else -> emptyList()
    }

/** Every block in document order, depth first. */
internal fun allBlocks(blocks: List<Block>): List<Block> =
    blocks.flatMap { b ->
        listOf(b) + childGroups(b).flatMap { allBlocks(it) }
    }
