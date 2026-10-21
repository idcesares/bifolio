package dev.dcesares.bifolio.math

/**
 * Rewrites of accepted source (ADR-0006). Each one is a text transformation that keeps the meaning of the formula
 * and removes something the renderer gets wrong or something the reading surface must not obey. They run on source
 * that [TexValidator] already accepted, so nesting is bounded by [MathLimits.MAX_DEPTH].
 */
@Suppress("TooManyFunctions") // One small text transformation per rule of ADR-0006, with its helpers.
internal object TexRewriter {
    private class Strip(
        val skip: Int,
        val keep: Boolean,
    )

    private val colorCommands =
        mapOf(
            "textcolor" to Strip(skip = 1, keep = true),
            "mathcolor" to Strip(skip = 1, keep = true),
            "colorbox" to Strip(skip = 1, keep = true),
            "fcolorbox" to Strip(skip = 2, keep = true),
            "color" to Strip(skip = 1, keep = false),
            "pagecolor" to Strip(skip = 1, keep = false),
            "bgcolor" to Strip(skip = 1, keep = false),
            "definecolor" to Strip(skip = 3, keep = false),
            // A link is inert: its text stays, the destination is dropped (no intent, no network, PRV-01).
            "href" to Strip(skip = 1, keep = true),
        )

    /**
     * Removes author colors (the theme foreground is the only color, so Night stays legible) and turns links into
     * their text. `\textcolor{red}{x}` becomes `{x}`; `\color{red}` disappears.
     */
    fun neutralise(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            if (src[i] != '\\') {
                out.append(src[i++])
                continue
            }
            val end = controlEnd(src, i)
            val name = src.substring(i + 1, end.coerceAtMost(src.length))
            val strip = colorCommands[name]
            if (name == "url") {
                i = urlAsText(src, i, end, out)
            } else if (strip != null) {
                i = strip(src, end, strip, out)
            } else {
                out.append(src, i, end)
                i = end
            }
        }
        return out.toString()
    }

    @Suppress("ReturnCount") // Each early return is a malformed argument: the command is then left as written.
    private fun strip(
        src: String,
        from: Int,
        spec: Strip,
        out: StringBuilder,
    ): Int {
        var at = from
        repeat(spec.skip) {
            at = skipSpace(src, skipOptional(src, skipSpace(src, at)))
            if (at >= src.length || src[at] != '{') return from
            at = groupEnd(src, at).takeIf { it > 0 } ?: return from
        }
        if (!spec.keep) return at
        val open = skipSpace(src, at)
        val body = groupText(src, open) ?: return at
        out.append('{').append(neutralise(body)).append('}')
        return groupEnd(src, open)
    }

    /** `\url{a}` becomes `\text{a}` with only the characters of a plain address kept. */
    private fun urlAsText(
        src: String,
        commandAt: Int,
        from: Int,
        out: StringBuilder,
    ): Int {
        val open = skipSpace(src, from)
        val text = groupText(src, open)
        if (text == null) {
            out.append(src, commandAt, from)
            return from
        }
        out.append("\\text{").append(text.filter { it.isLetterOrDigit() || it in "./:-?=+~" }).append('}')
        return groupEnd(src, open)
    }

    private const val CASES_OPEN = "\\begin{cases}"
    private const val CASES_CLOSE = "\\end{cases}"
    private const val ARRAY_OPEN = "\\left\\{\\begin{array}{ll}"
    private const val QUAD_SLACK = 16
    private const val ARRAY_CLOSE = "\\end{array}\\right."

    /**
     * The renderer draws `cases` with its columns swapped and an injected "if" (ADR-0006). The same formula as a
     * two column `array` inside a left brace draws correctly. A `\quad` opens the second column, like amsmath.
     */
    fun casesToArray(src: String): String {
        val start = src.indexOf(CASES_OPEN)
        if (start < 0) return src
        val end = matchingCasesEnd(src, start + CASES_OPEN.length) ?: return src
        val body = casesToArray(src.substring(start + CASES_OPEN.length, end))
        val after = casesToArray(src.substring(end + CASES_CLOSE.length))
        return src.substring(0, start) + ARRAY_OPEN + spaceSecondColumn(body) + ARRAY_CLOSE + after
    }

    private fun matchingCasesEnd(
        src: String,
        from: Int,
    ): Int? {
        var depth = 1
        var i = from
        while (i < src.length) {
            val close = src.indexOf(CASES_CLOSE, i)
            val open = src.indexOf(CASES_OPEN, i)
            if (close < 0) return null
            if (open in 0 until close) {
                depth++
                i = open + CASES_OPEN.length
            } else if (--depth == 0) {
                return close
            } else {
                i = close + CASES_CLOSE.length
            }
        }
        return null
    }

    /** The first `&` of each row (outside groups and nested environments) gets `\quad` after it. */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth") // One scan with three kinds of token to track.
    private fun spaceSecondColumn(body: String): String {
        val out = StringBuilder(body.length + QUAD_SLACK)
        var depth = 0
        var firstCell = true
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') {
                val end = controlEnd(body, i)
                when (body.substring(i + 1, end.coerceAtMost(body.length))) {
                    "begin" -> depth++
                    "end" -> depth--
                    "\\" -> if (depth == 0) firstCell = true
                }
                out.append(body, i, end)
                i = end
                continue
            }
            if (c == '{') depth++
            if (c == '}') depth--
            if (c == '&' && depth == 0 && firstCell) {
                firstCell = false
                out.append("&\\quad ")
                while (i + 1 < body.length && body[i + 1].isWhitespace()) i++
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private val verbatimGroup =
        setOf(
            "text",
            "textrm",
            "textbf",
            "textit",
            "textsf",
            "texttt",
            "mbox",
            "hbox",
            "mathrm",
            "operatorname",
            "label",
            "tag",
            "hspace",
            "vspace",
            "mspace",
            "begin",
            "end",
            "rule",
        )
    private val bareLengths = setOf("kern", "mkern", "hskip", "vskip", "mskip")
    private val columnSpecEnvironments = setOf("array", "tabular", "subarray", "alignat", "alignedat")
    private const val MINUS = '−'

    /**
     * The renderer draws `-` with the hyphen glyph, too short and too low for a book (ADR-0006). In math mode it
     * is U+2212. Text arguments, names, column specs and lengths keep their hyphen.
     */
    fun trueMinus(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when (c) {
                '-' -> {
                    out.append(MINUS)
                    i++
                }
                '\\' -> i = copyCommand(src, i, out)
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Copies the control sequence at [at] and the arguments in which `-` must stay; returns the index after. */
    private fun copyCommand(
        src: String,
        at: Int,
        out: StringBuilder,
    ): Int {
        val end = controlEnd(src, at)
        val name = src.substring(at + 1, end.coerceAtMost(src.length))
        val next =
            when {
                name in verbatimGroup -> verbatimArguments(src, end, name)
                name in bareLengths -> bareLengthEnd(src, end)
                name == "\\" -> skipOptional(src, end)
                else -> end
            }
        out.append(src, at, next)
        return if (name == "begin") copyColumnSpec(src, at + "\\begin".length, next, out) else next
    }

    private fun verbatimArguments(
        src: String,
        from: Int,
        name: String,
    ): Int {
        var at = from
        if (at < src.length && src[at] == '*') at++
        at = skipOptional(src, at)
        repeat(if (name == "rule") 2 else 1) {
            val open = skipSpace(src, at)
            val end = if (open < src.length && src[open] == '{') groupEnd(src, open) else -1
            if (end < 0) return at
            at = end
        }
        return at
    }

    private fun bareLengthEnd(
        src: String,
        from: Int,
    ): Int {
        var at = skipSpace(src, from)
        while (at < src.length && isLengthChar(src[at])) at++
        return at
    }

    /** After `\begin{array}` the `{ll|r}` column spec (and a `[t]`) is copied unchanged. */
    private fun copyColumnSpec(
        src: String,
        nameFrom: Int,
        afterName: Int,
        out: StringBuilder,
    ): Int {
        val name = groupText(src, skipSpace(src, nameFrom)) ?: return afterName
        if (name !in columnSpecEnvironments) return afterName
        val open = skipSpace(src, skipOptional(src, afterName))
        val end = if (open < src.length && src[open] == '{') groupEnd(src, open) else -1
        if (end < 0) return afterName
        out.append(src, afterName, end)
        return end
    }
}
