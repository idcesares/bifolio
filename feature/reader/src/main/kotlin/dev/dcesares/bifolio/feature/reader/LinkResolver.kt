package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Block
import java.net.URLDecoder

/** What a tapped link destination asks the reader to do (NAV-07, NAV-08). */
internal sealed interface LinkAction {
    /** Show [anchor]: a heading or a footnote definition. The caller starts the return chip (ANC-06). */
    data class Jump(
        val anchor: Anchor,
    ) : LinkAction

    /** A web or mail address. Opening it needs the confirmation of NAV-08; until then nothing opens it. */
    data class External(
        val url: String,
    ) : LinkAction

    /** Nothing to do: unknown fragment, relative path or a scheme the reader refuses. */
    data object None : LinkAction
}

private val Schemes = setOf("http", "https", "mailto")
private val NotePrefixes = listOf("", "^", "fn:", "fn-", "fn_")

/**
 * Resolves link destinations inside one document (NAV-07). `#slug` goes to the heading, falling back to a
 * footnote label (also as `#fn:label` or `#^label`); the fragment is percent-decoded and compared ignoring case.
 * Only http, https and mailto count as external; every other scheme (javascript, file, content, intent, data)
 * is refused so a document can never reach another app or a local file through a link.
 */
internal class LinkResolver(
    blocks: List<Block>,
) {
    private val slugs = SlugIndex(blocks)
    private val notes = NoteIndex(blocks)

    fun resolve(destination: String): LinkAction {
        val text = destination.trim()
        if (text.isEmpty()) return LinkAction.None
        if (text.startsWith('#')) return internal(text)
        val colon = text.indexOf(':')
        val scheme = if (colon > 0) text.substring(0, colon).lowercase() else null
        return if (scheme != null && scheme in Schemes) LinkAction.External(text) else LinkAction.None
    }

    private fun internal(fragment: String): LinkAction {
        slugs.blockIndexOf(fragment)?.let { return LinkAction.Jump(Anchor(it, 0)) }
        val raw = fragment.substring(1)
        if (raw.isEmpty()) return LinkAction.None
        val decoded = decode(raw)
        for (prefix in NotePrefixes) {
            val label = if (prefix.isEmpty()) decoded else decoded.removePrefixIgnoreCase(prefix) ?: continue
            notes.definitionOf(label)?.let { return LinkAction.Jump(Anchor(it.blockIndex, 0)) }
        }
        return LinkAction.None
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String? =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null

    private fun decode(text: String): String =
        try {
            URLDecoder.decode(text.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            text
        }

    /** The definition for [label] (UI-09), or null: an undefined reference is plain text and never gets here. */
    fun definitionOf(label: String): NoteDefinition? = notes.definitionOf(label)
}
