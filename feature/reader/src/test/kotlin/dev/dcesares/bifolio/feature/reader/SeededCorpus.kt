package dev.dcesares.bifolio.feature.reader

import kotlin.random.Random

/**
 * Seeded generator for the corpus documents that are too large to commit (docs/TESTING.md C-02 and the
 * 1 MB document of PRF-04). The same seed always gives the same bytes.
 */
internal object SeededCorpus {
    private val words =
        """
        the of and to in that is was for with as his on be at by this had not are but from or have an they which
        one you were her all she there would their we him been has when who will more no if out so said what up
        its about into than them can only other new some could time these two may then do first any my now such
        like our over man me even most made after also did many before must through back years where much your
        way well down should because each just those people how too little state good very make world still own
        see men work long get here between both life being under never day same another know while last might
        us great old year off come since against go came right used take three
        """.trim().split(Regex("""\s+"""))

    /** A document of about [targetWords] words: chapters, paragraphs, and now and then a list, quote, code or table. */
    fun document(
        seed: Int,
        targetWords: Int,
    ): String {
        val rng = Random(seed)
        val out = StringBuilder()
        var count = 0
        var chapter = 0
        while (count < targetWords) {
            chapter++
            out
                .append("# Chapter ")
                .append(chapter)
                .append(": ")
                .append(title(rng))
                .append("\n\n")
            count += 4
            repeat(rng.nextInt(CHAPTER_MIN_BLOCKS, CHAPTER_MAX_BLOCKS)) {
                count += block(rng, out)
                if (count >= targetWords) return out.toString()
            }
        }
        return out.toString()
    }

    private fun title(rng: Random): String = (1..rng.nextInt(2, 5)).joinToString(" ") { words[rng.nextInt(words.size)] }

    private fun sentence(rng: Random): String {
        val n = rng.nextInt(SENTENCE_MIN, SENTENCE_MAX)
        val text = (1..n).joinToString(" ") { words[rng.nextInt(words.size)] }
        return text.replaceFirstChar { it.uppercase() } + "."
    }

    private fun paragraph(rng: Random): Pair<String, Int> {
        val sentences = (1..rng.nextInt(PARAGRAPH_MIN, PARAGRAPH_MAX)).map { sentence(rng) }
        val text = sentences.joinToString(" ")
        return text to text.count { it == ' ' } + 1
    }

    /** Appends one block and returns how many words it holds. */
    private fun block(
        rng: Random,
        out: StringBuilder,
    ): Int {
        val (text, words) = paragraph(rng)
        return when (rng.nextInt(KINDS)) {
            0 -> {
                val items = List(rng.nextInt(2, 6)) { sentence(rng) }
                items.forEach { out.append("- ").append(it).append('\n') }
                out.append('\n')
                items.sumOf { it.count { c -> c == ' ' } + 1 }
            }
            1 -> {
                out.append("> ").append(text).append("\n\n")
                words
            }
            2 -> {
                out
                    .append("## ")
                    .append(title(rng))
                    .append("\n\n")
                    .append(text)
                    .append("\n\n")
                words + 2
            }
            3 -> {
                out.append("```\n")
                repeat(rng.nextInt(CODE_MIN, CODE_MAX)) {
                    out
                        .append("let v")
                        .append(it)
                        .append(" = ")
                        .append(rng.nextInt(1000))
                        .append('\n')
                }
                out.append("```\n\n")
                CODE_WORDS
            }
            else -> {
                out.append(text).append("\n\n")
                words
            }
        }
    }

    private const val CHAPTER_MIN_BLOCKS = 12
    private const val CHAPTER_MAX_BLOCKS = 30
    private const val SENTENCE_MIN = 8
    private const val SENTENCE_MAX = 22
    private const val PARAGRAPH_MIN = 3
    private const val PARAGRAPH_MAX = 8
    private const val KINDS = 8
    private const val CODE_MIN = 4
    private const val CODE_MAX = 14
    private const val CODE_WORDS = 40
}
