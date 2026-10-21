package dev.dcesares.bifolio.math

/** The outcome of [TexSanitizer.prepare]. */
public sealed interface PreparedTex {
    /** [tex] is safe to hand to the renderer. It differs from the author's source (colors, links, `cases`, minus). */
    public data class Ready(
        val tex: String,
    ) : PreparedTex

    public data class Rejected(
        val failure: MathFailure,
    ) : PreparedTex
}

/**
 * Everything done to the author's TeX before the renderer sees it (ADR-0006 guards 2, 3 and the rewrites). Pure
 * Kotlin, so it is tested on the JVM with the hostile corpus (C-15). The original source is kept by the caller for
 * the fallback and for the content description (A11Y-04); only the prepared text reaches the renderer.
 */
public object TexSanitizer {
    public fun prepare(source: String): PreparedTex {
        TexValidator.validate(source)?.let { return PreparedTex.Rejected(it) }
        val rewritten = TexRewriter.trueMinus(TexRewriter.casesToArray(TexRewriter.neutralise(source)))
        // A rewrite must never produce what the validator refuses; checking again costs microseconds.
        TexValidator.validate(rewritten)?.let { return PreparedTex.Rejected(it) }
        return PreparedTex.Ready(rewritten)
    }
}
