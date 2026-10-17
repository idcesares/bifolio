package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor

/**
 * A run of consecutive lines of one top-level block placed on one page (ADR-0004).
 *
 * Lines are the flattened lines of the block, numbered from 0 (containers flatten their children in
 * reading order; a table row and an atomic block are one line each). `[startLine, endLine)` is half-open.
 *
 * A continuation (`startLine > 0`) of a code block shows the continuation mark (PAG-04); of a quote or
 * callout it continues the rule without repeating a title (PAG-05); of a table it repeats the header row
 * (PAG-06), whose height is already part of [height] and is reported in [repeatedHeaderHeight].
 */
public data class Slice(
    val blockIndex: Int,
    val startLine: Int,
    val endLine: Int,
    /** Height in px used on the page, including a repeated table header. */
    val height: Int,
    /** True when more lines of the same block follow on the next page. */
    val continuesOnNextPage: Boolean,
    val repeatedHeaderHeight: Int,
) {
    init {
        require(
            blockIndex >= 0 && startLine >= 0 && endLine > startLine,
        ) { "bad slice $blockIndex $startLine $endLine" }
    }

    public val lineCount: Int get() = endLine - startLine

    /** True when this slice continues a block started on an earlier page. */
    public val isContinuation: Boolean get() = startLine > 0
}

/**
 * One column of text. [anchor] is the identity of the page (PAG-11): the anchor of its first slice. It is
 * never an index. A spread is `columnCount` consecutive pages (PAG-12).
 */
public data class Page(
    val slices: List<Slice>,
    val anchor: Anchor,
    /** Height in px used by the slices and the gaps between them. */
    val usedHeight: Int,
    /** True for the last page of the document, which ends with the end ornament (PAG-12). */
    val endsDocument: Boolean,
)

/** Reading-order comparison of two anchors. */
public fun Anchor.compareReadingOrder(other: Anchor): Int =
    if (blockIndex !=
        other.blockIndex
    ) {
        blockIndex.compareTo(other.blockIndex)
    } else {
        charOffset.compareTo(other.charOffset)
    }

/** Polled by long-running layout work; return true to stop as soon as possible (PAG-09). */
public fun interface CancellationSignal {
    public fun isCancelled(): Boolean

    public companion object {
        public val NEVER: CancellationSignal = CancellationSignal { false }
    }
}
