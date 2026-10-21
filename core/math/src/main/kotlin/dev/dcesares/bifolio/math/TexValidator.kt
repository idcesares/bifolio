package dev.dcesares.bifolio.math

import kotlin.math.abs

/**
 * Structural checks run on the author's source before the renderer sees it (ADR-0006 guard 2). The renderer
 * accepts several malformed inputs without a diagnostic and overflows on deep ones, so everything it cannot be
 * trusted with is refused here. Pure Kotlin, no recursion.
 */
@Suppress("TooManyFunctions") // One small scanner function per TeX construct keeps each guard readable.
internal object TexValidator {
    private val macroCommands =
        setOf(
            "def",
            "gdef",
            "edef",
            "xdef",
            "let",
            "futurelet",
            "newcommand",
            "renewcommand",
            "providecommand",
            "newenvironment",
            "renewenvironment",
            "DeclareMathOperator",
            "newtheorem",
            "csname",
            "input",
            "include",
            "write",
            "read",
            "openin",
            "openout",
            "catcode",
            "makeatletter",
            "expandafter",
        )
    private val twoOperands = setOf("frac", "dfrac", "tfrac", "cfrac", "binom", "dbinom", "tbinom")
    private val oneOperand = setOf("sqrt")
    private val operandCommands = twoOperands + oneOperand
    private val groupDimensions = setOf("hspace", "vspace", "mspace")
    private val bareDimensions = setOf("kern", "mkern", "hskip", "vskip", "mskip")

    @Suppress("ReturnCount") // Each early return is one named failure of ADR-0006 guard 2.
    fun validate(src: String): MathFailure? {
        if (src.isBlank()) return MathFailure.EMPTY
        if (src.length > MathLimits.MAX_LENGTH) return MathFailure.TOO_LONG
        val state = ScanState()
        var i = 0
        while (i < src.length) {
            val failure: MathFailure?
            when (src[i]) {
                '%' -> return MathFailure.DIMENSION
                '{' -> failure = state.open() ?: state.settle()
                '}' -> failure = state.close() ?: state.settle()
                '^', '_' -> failure = script(src, i, state)
                ' ', '\t', '\n', '\r' -> failure = null
                '\\' -> {
                    val step = command(src, i, state)
                    failure = step.failure
                    i = step.next - 1
                }
                else -> failure = state.settle()
            }
            if (failure != null) return failure
            i++
        }
        return state.finish()
    }

    private class Step(
        val next: Int,
        val failure: MathFailure? = null,
    )

    private class ScanState {
        var depth = 0
        var lefts = 0

        // Operand taking tokens that follow each other with no group between them (`racracrac...`, `x^^^`) nest in
        // the renderer's parser without a brace to count, so they are counted here against the same limit.
        private var chain = 0
        private var expecting = false

        fun chained(): MathFailure? {
            chain = if (expecting) chain + 1 else 1
            expecting = true
            return if (chain > MathLimits.MAX_DEPTH) MathFailure.TOO_DEEP else null
        }

        fun settle(): MathFailure? {
            expecting = false
            return null
        }

        val environments = ArrayDeque<String>()

        fun open(): MathFailure? = if (++depth > MathLimits.MAX_DEPTH) MathFailure.TOO_DEEP else null

        fun left(): MathFailure? {
            lefts++
            return open()
        }

        fun right(): MathFailure? {
            depth--
            return if (--lefts < 0) MathFailure.UNBALANCED else null
        }

        fun close(): MathFailure? = if (--depth < 0) MathFailure.UNBALANCED else null

        fun finish(): MathFailure? =
            if (depth != 0 || lefts != 0 || environments.isNotEmpty()) MathFailure.UNBALANCED else null
    }

    private fun script(
        src: String,
        at: Int,
        state: ScanState,
    ): MathFailure? = state.chained() ?: if (operandEnd(src, at + 1) == null) MathFailure.MISSING_OPERAND else null

    private fun command(
        src: String,
        at: Int,
        state: ScanState,
    ): Step {
        val end = controlEnd(src, at)
        val name = src.substring(at + 1, end.coerceAtMost(src.length))
        val entered = if (name in operandCommands) state.chained() else state.settle()
        if (entered != null) return Step(end, entered)
        return when {
            name == "begin" -> environment(src, end, state, opening = true)
            name == "end" -> environment(src, end, state, opening = false)
            name == "left" -> Step(end, state.left())
            name == "right" -> Step(end, state.right())
            name in macroCommands -> Step(end, MathFailure.MACRO)
            name in twoOperands -> operands(src, end, 2)
            name in oneOperand -> operands(src, skipOptional(src, end), 1)
            else -> spacing(src, name, end)
        }
    }

    private fun spacing(
        src: String,
        name: String,
        end: Int,
    ): Step =
        when {
            name == "rule" -> rule(src, end)
            name in groupDimensions -> groupDimension(src, end)
            name in bareDimensions -> bareDimension(src, end)
            name == "\\" -> rowGap(src, end)
            else -> Step(end)
        }

    private fun environment(
        src: String,
        from: Int,
        state: ScanState,
        opening: Boolean,
    ): Step {
        val at = skipSpace(src, from)
        val name = groupText(src, at) ?: return Step(from, MathFailure.UNBALANCED)
        val next = groupEnd(src, at)
        if (opening) {
            state.environments.addLast(name)
            val tooDeep = ++state.depth > MathLimits.MAX_DEPTH
            return Step(next, if (tooDeep) MathFailure.TOO_DEEP else null)
        }
        state.depth--
        val open = state.environments.removeLastOrNull()
        return Step(next, if (open != name) MathFailure.UNBALANCED else null)
    }

    private fun operands(
        src: String,
        from: Int,
        count: Int,
    ): Step {
        var at = from
        repeat(count) {
            at = operandEnd(src, at) ?: return Step(from, MathFailure.MISSING_OPERAND)
        }
        // Resume right after the command so the operands are scanned for their own braces and commands.
        return Step(from)
    }

    private fun rule(
        src: String,
        from: Int,
    ): Step {
        var at = skipOptional(src, from)
        repeat(2) {
            at = skipSpace(src, at)
            val text = groupText(src, at) ?: return Step(from, MathFailure.DIMENSION)
            if (!dimensionIsSane(text)) return Step(from, MathFailure.DIMENSION)
            at = groupEnd(src, at)
        }
        return Step(from)
    }

    private fun groupDimension(
        src: String,
        from: Int,
    ): Step {
        var at = from
        if (at < src.length && src[at] == '*') at++
        at = skipSpace(src, at)
        val text = groupText(src, at)
        return Step(from, if (text != null && dimensionIsSane(text)) null else MathFailure.DIMENSION)
    }

    private fun bareDimension(
        src: String,
        from: Int,
    ): Step {
        val start = skipSpace(src, from)
        var end = start
        while (end < src.length && isLengthChar(src[end])) end++
        return Step(from, if (dimensionIsSane(src.substring(start, end))) null else MathFailure.DIMENSION)
    }

    /** `\[2pt]`: the optional length after a row break. */
    private fun rowGap(
        src: String,
        from: Int,
    ): Step {
        val at = skipSpace(src, from)
        if (at >= src.length || src[at] != '[') return Step(from)
        val close = src.indexOf(']', at)
        val sane = close > 0 && dimensionIsSane(src.substring(at + 1, close))
        return Step(from, if (sane) null else MathFailure.DIMENSION)
    }

    private const val DIMENSION_UNIT_GROUP = 3

    private val dimension = Regex("""\s*([+-]?)\s*(\d{1,6}(?:\.\d{1,6})?|\.\d{1,6})\s*([a-z]{2})\s*""")

    private val emPerUnit =
        mapOf(
            "em" to 1f,
            "ex" to 0.5f,
            "pt" to 0.1f,
            "px" to 0.1f,
            "bp" to 0.1f,
            "dd" to 0.107f,
            "pc" to 1.2f,
            "cc" to 1.28f,
            "mm" to 0.2845f,
            "cm" to 2.845f,
            "in" to 7.227f,
            "mu" to 0.0556f,
            "sp" to 0f,
        )

    /** True when [text] is a length like `2pt` whose size is at most [MathLimits.MAX_DIMENSION_EM] em. */
    fun dimensionIsSane(text: String): Boolean {
        val match = dimension.matchEntire(text) ?: return false
        val perUnit = emPerUnit[match.groupValues[DIMENSION_UNIT_GROUP]] ?: return false
        val value = match.groupValues[2].toFloatOrNull() ?: return false
        return abs(value * perUnit) <= MathLimits.MAX_DIMENSION_EM
    }
}
