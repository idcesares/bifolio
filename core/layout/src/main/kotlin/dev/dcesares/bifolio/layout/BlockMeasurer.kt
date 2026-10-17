package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block

/** Typographic role of a text run. The measurer maps it to font, size and line height. */
public enum class TextRole {
    BODY,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    HEADING_4,
    HEADING_5,
    HEADING_6,
    CODE,
    TABLE_CELL,
}

/**
 * Lines of one text run, as parallel arrays of [count] entries (ADR-0004).
 *
 * Contract, checked by the layout tests against every implementation:
 * - lines are in reading order and tile the text: `starts[0] == 0`, `ends[i] == starts[i + 1]`,
 *   `ends[count - 1] == text.length`;
 * - `tops[i] < bottoms[i]` and `tops[i + 1] >= bottoms[i]`, in px from the top of the run;
 * - an empty text has zero lines.
 *
 * Arrays are owned by the result; callers must not mutate them.
 */
public class LineMetrics(
    public val count: Int,
    public val tops: IntArray,
    public val bottoms: IntArray,
    public val starts: IntArray,
    public val ends: IntArray,
) {
    init {
        require(tops.size >= count && bottoms.size >= count && starts.size >= count && ends.size >= count) {
            "arrays shorter than count $count"
        }
    }

    /** Total height of the run in px. */
    public val height: Int get() = if (count == 0) 0 else bottoms[count - 1] - tops[0]
}

/** Intrinsic size in px of an atomic block (image, display math, front matter card) at a given width. */
public data class AtomicSize(
    val width: Int,
    val height: Int,
)

/**
 * Text measurement seam between the pure pagination code and the platform (ADR-0004, OPN-05).
 * Implemented in `:core:text`; a deterministic fake is used in tests. Implementations must be
 * callable from a background thread and must not allocate per line beyond the returned arrays.
 */
public interface BlockMeasurer {
    /** Breaks [text] into lines for [role] within [widthPx]. Honors `\n` as a hard line break. */
    public fun measureLines(
        text: String,
        role: TextRole,
        widthPx: Int,
    ): LineMetrics

    /** Width in px of the longest unbroken line of [text] for [role] (used for table column sizing). */
    public fun intrinsicWidth(
        text: String,
        role: TextRole,
    ): Int

    /** Natural size of an atomic [block] when laid out at [widthPx]. The paginator scales it (PAG-07). */
    public fun measureAtomic(
        block: Block,
        widthPx: Int,
    ): AtomicSize
}
