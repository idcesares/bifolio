package dev.dcesares.bifolio.parse

import java.util.Locale

/**
 * GitHub-style heading ids (SYN-01): lowercase, keep letters, marks, numbers, connector punctuation and
 * hyphens in any script, spaces become hyphens, everything else is dropped. Repeated ids get `-1`, `-2`...
 * One instance per document.
 */
internal class SlugGenerator {
    private val used = HashSet<String>()
    private val lastSuffix = HashMap<String, Int>()

    fun next(headingText: String): String {
        val base = slugify(headingText)
        if (used.add(base)) return base
        var n = lastSuffix[base] ?: 0
        while (true) {
            n++
            val candidate = "$base-$n"
            if (used.add(candidate)) {
                lastSuffix[base] = n
                return candidate
            }
        }
    }

    companion object {
        fun slugify(text: String): String {
            val lower = text.trim().lowercase(Locale.ROOT)
            val sb = StringBuilder(lower.length)
            var i = 0
            while (i < lower.length) {
                val cp = lower.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    cp == '-'.code -> sb.append('-')
                    Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> sb.append('-')
                    isKept(cp) -> sb.appendCodePoint(cp)
                }
            }
            return sb.toString()
        }

        private fun isKept(cp: Int): Boolean {
            if (Character.isLetterOrDigit(cp)) return true
            return when (Character.getType(cp).toByte()) {
                Character.NON_SPACING_MARK,
                Character.COMBINING_SPACING_MARK,
                Character.ENCLOSING_MARK,
                Character.LETTER_NUMBER,
                Character.OTHER_NUMBER,
                Character.CONNECTOR_PUNCTUATION,
                -> true
                else -> false
            }
        }
    }
}
