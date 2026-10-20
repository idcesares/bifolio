package dev.dcesares.bifolio.layout

/** Bit flags of the inline styling of a text run (SYN-02, SYN-07, DOC-09). */
public object StyleFlag {
    public const val BOLD: Int = 1
    public const val ITALIC: Int = 2
    public const val STRIKETHROUGH: Int = 4
    public const val CODE: Int = 8
    public const val LINK: Int = 16
    public const val SUPERSCRIPT: Int = 32
    public const val SUBSCRIPT: Int = 64
    public const val MARK: Int = 128
    public const val UNDERLINE: Int = 256

    /** A footnote marker (SYN-09): superscript in the accent color. */
    public const val NOTE: Int = 512
}

/**
 * Inline styling of one rendered text, as disjoint runs `[starts[i], ends[i])` carrying [flags]. Styles change
 * glyph widths (bold, italic, monospace, scripts), so the measurer receives them and the page is drawn from
 * the same styled text it was broken with (ADR-0013). Unstyled text has no run. Immutable.
 */
public class TextStyles(
    public val starts: IntArray,
    public val ends: IntArray,
    public val flags: IntArray,
) {
    init {
        require(starts.size == ends.size && ends.size == flags.size) { "run arrays differ in size" }
    }

    public val count: Int get() = starts.size

    override fun equals(other: Any?): Boolean =
        other is TextStyles &&
            starts.contentEquals(other.starts) &&
            ends.contentEquals(other.ends) &&
            flags.contentEquals(other.flags)

    override fun hashCode(): Int =
        (starts.contentHashCode() * HASH + ends.contentHashCode()) * HASH + flags.contentHashCode()

    public companion object {
        public val NONE: TextStyles = TextStyles(IntArray(0), IntArray(0), IntArray(0))
        private const val HASH = 31
    }
}
