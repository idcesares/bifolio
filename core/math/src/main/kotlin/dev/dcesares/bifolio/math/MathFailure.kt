package dev.dcesares.bifolio.math

/**
 * Why a formula is not drawn (SYN-12). Every value means the same thing to the reader: show the TeX source in
 * monospace with the quiet error mark. The reason exists for tests and for the performance log, not for the user.
 */
public enum class MathFailure {
    /** Nothing to draw: empty or blank source. */
    EMPTY,

    /** Longer than [MathLimits.MAX_LENGTH] characters. */
    TOO_LONG,

    /** Braces, `\begin` or `\left` nest deeper than [MathLimits.MAX_DEPTH]. */
    TOO_DEEP,

    /** Unbalanced braces, `\left`/`\right` or `\begin`/`\end`, or an environment closed by another name. */
    UNBALANCED,

    /** A command lacks its operand (`\frac{a}`, `x^`). */
    MISSING_OPERAND,

    /** `\def`, `\newcommand` and friends, or a command that reads files. Macros can grow without bound. */
    MACRO,

    /** A TeX comment (`%`) or a dimension (`\rule`, `\hspace`, `\kern`) that cannot be read or is absurdly large. */
    DIMENSION,

    /** The renderer's parser reported an error, or the formula holds an unknown command. */
    PARSE,

    /** The renderer could not measure the formula, or measured an absurd size. */
    SIZE,

    /** A call took longer than [MathLimits.CALL_BUDGET_MILLIS]; the formula is not measured or drawn again. */
    TIMEOUT,

    /** The renderer threw (including stack and memory exhaustion). */
    RENDERER,
}

/** The numbers behind the guards of ADR-0006. They are deliberately conservative: a real formula is far inside. */
public object MathLimits {
    /** Longest accepted source, in characters. The longest formula of the corpus is about 150. */
    public const val MAX_LENGTH: Int = 2_000

    /**
     * Deepest nesting of braces, environments and `\left`. Worker threads have small stacks and the renderer
     * recurses once per level; 500 levels overflowed the stack on the JVM (ADR-0006).
     */
    public const val MAX_DEPTH: Int = 32

    /** Largest dimension an author may write, in em (`\hspace{99999999pt}` is 2e8 px wide). */
    public const val MAX_DIMENSION_EM: Float = 24f

    /** A measured formula wider than this many em of the font size is rejected. */
    public const val MAX_WIDTH_EM: Float = 150f

    /** A measured formula taller than this many em of the font size is rejected. */
    public const val MAX_HEIGHT_EM: Float = 60f

    /**
     * The longest one measurement or drawing may take. The library cannot be interrupted, so the call is answered
     * late and the formula is then treated as failed, so it is not measured or drawn again.
     */
    public const val CALL_BUDGET_MILLIS: Long = 2_000L
}
