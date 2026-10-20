package dev.dcesares.bifolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.dcesares.bifolio.designsystem.CodeTokenColors
import dev.dcesares.bifolio.highlight.CodeHighlighter
import dev.dcesares.bifolio.highlight.RuleBasedHighlighter
import dev.dcesares.bifolio.highlight.TokenKind
import dev.dcesares.bifolio.highlight.TokenSpan
import dev.dcesares.bifolio.text.ColorRuns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Token spans of code blocks (SYN-11, ADR-0007), computed once per block and shared by the paged and the scroll
 * surface. The key is the language and the code text only, never the theme: switching theme re-colors from the same
 * spans and does not tokenize again. Callers run off the main thread; the highlighter is a single linear pass.
 */
internal object CodeTokenCache {
    private const val CAPACITY = 64

    private data class Key(
        val language: String,
        val code: String,
    )

    private val highlighter: CodeHighlighter = RuleBasedHighlighter()
    private val entries =
        object : LinkedHashMap<Key, List<TokenSpan>>(CAPACITY, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<TokenSpan>>): Boolean =
                size > CAPACITY
        }

    /** Spans of [code] in [language], or null when the language is absent or unknown: the block stays plain. */
    fun spansOf(
        code: String,
        language: String?,
    ): List<TokenSpan>? {
        if (language == null || code.isEmpty() || !highlighter.supports(language)) return null
        val key = Key(language, code)
        synchronized(entries) { entries[key] }?.let { return it }
        val spans = highlighter.tokenize(code, language)
        synchronized(entries) { entries[key] = spans }
        return spans
    }

    /** The cached spans, or null when not tokenized yet. Cheap enough for the main thread. */
    fun peek(
        code: String,
        language: String?,
    ): List<TokenSpan>? {
        if (language == null) return null
        return synchronized(entries) { entries[Key(language, code)] }
    }
}

internal fun CodeTokenColors.colorOf(kind: TokenKind): Color? =
    when (kind) {
        TokenKind.KEYWORD -> keyword
        TokenKind.STRING -> string
        TokenKind.NUMBER -> number
        TokenKind.COMMENT -> comment
        TokenKind.TYPE -> type
        TokenKind.FUNCTION -> function
        TokenKind.OPERATOR -> operator
        TokenKind.PUNCTUATION -> punctuation
        TokenKind.PLAIN -> null
    }

/** Color runs for the paged surface; plain spans are left out so they keep `ink`. */
internal fun colorRunsOf(
    spans: List<TokenSpan>,
    colors: CodeTokenColors,
): ColorRuns? {
    val kept = spans.filter { colors.colorOf(it.kind) != null }
    if (kept.isEmpty()) return null
    return ColorRuns(
        IntArray(kept.size) { kept[it].start },
        IntArray(kept.size) { kept[it].end },
        IntArray(kept.size) { checkNotNull(colors.colorOf(kept[it].kind)).toArgb() },
    )
}

/** Colored text for the scroll surface: color spans only, the font and its advance stay those of plain code. */
internal fun codeAnnotated(
    code: String,
    spans: List<TokenSpan>,
    colors: CodeTokenColors,
): AnnotatedString =
    buildAnnotatedString {
        append(code)
        for (span in spans) {
            val color = colors.colorOf(span.kind) ?: continue
            addStyle(SpanStyle(color = color), span.start.coerceAtLeast(0), span.end.coerceAtMost(code.length))
        }
    }

/**
 * Spans of [code] for the scroll surface: the cached ones at once, else computed on `Dispatchers.Default` while the
 * block shows plain, so scrolling to a long block never waits on the tokenizer.
 */
@Composable
internal fun rememberTokenSpans(
    code: String,
    language: String?,
): List<TokenSpan>? {
    val initial = remember(code, language) { CodeTokenCache.peek(code, language) }
    val spans by produceState(initial, code, language) {
        if (value == null && language != null) {
            value = withContext(Dispatchers.Default) { CodeTokenCache.spansOf(code, language) }
        }
    }
    return spans
}
