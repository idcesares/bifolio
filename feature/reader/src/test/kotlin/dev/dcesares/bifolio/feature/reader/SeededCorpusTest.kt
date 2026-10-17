package dev.dcesares.bifolio.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** C-02 and the 1 MB document are generated, never committed; the same seed always gives the same text. */
class SeededCorpusTest {
    @Test
    fun `the generator is deterministic and writes the large documents for the device runs`() {
        val long = SeededCorpus.document(seed = 2, targetWords = 60_000)
        assertEquals(long, SeededCorpus.document(seed = 2, targetWords = 60_000))
        val words = long.split(Regex("\\s+")).size
        assertTrue(words in 55_000..75_000, "about 60k words, was $words")
        val megabyte = SeededCorpus.document(seed = 3, targetWords = 215_000)
        assertTrue(megabyte.toByteArray().size in 900_000..1_300_000, "about 1 MB, was ${megabyte.length}")
        val dir = File("build/corpus").apply { mkdirs() }
        File(dir, "prose-long.md").writeText(long)
        File(dir, "prose-1mb.md").writeText(megabyte)
    }
}
