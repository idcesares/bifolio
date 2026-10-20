package dev.dcesares.bifolio.model

/**
 * Reading position independent of geometry (ADR-0005, ANC-01).
 * [charOffset] counts source characters from the start of the [SourceRange] of block [blockIndex].
 */
public data class Anchor(
    val blockIndex: Int,
    val charOffset: Int,
) {
    init {
        require(blockIndex >= 0) { "blockIndex must be >= 0: $blockIndex" }
        require(charOffset >= 0) { "charOffset must be >= 0: $charOffset" }
    }
}

/** One parsed document: normalized [source] plus the immutable top-level [blocks] (DOC-06, DOC-07). */
public data class Document(
    val title: String?,
    val source: String,
    val blocks: List<Block>,
    /** Document language from front matter `lang` (SYN-13, TYP-05); null when unspecified. */
    val language: String? = null,
) {
    /**
     * Anchor of the source character at [sourceOffset] (clamped to the source). An offset between blocks
     * snaps to the start of the next block, or to the end of the last block.
     */
    public fun anchorAt(sourceOffset: Int): Anchor {
        val offset = sourceOffset.coerceIn(0, source.length)
        val found = lastBlockStartingAtOrBefore(offset)
        if (found < 0) return Anchor(0, 0)
        val range = blocks[found].range
        return when {
            offset <= range.end -> Anchor(found, offset - range.start)
            found + 1 < blocks.size -> Anchor(found + 1, 0)
            else -> Anchor(found, range.length)
        }
    }

    /** Source offset of [anchor], clamped inside its block; an out-of-range index clamps to the last block end. */
    public fun sourceOffsetOf(anchor: Anchor): Int =
        when {
            blocks.isEmpty() -> 0
            anchor.blockIndex >= blocks.size -> blocks.last().range.end
            else -> {
                val range = blocks[anchor.blockIndex].range
                range.start + anchor.charOffset.coerceAtMost(range.length)
            }
        }

    private fun lastBlockStartingAtOrBefore(offset: Int): Int {
        var lo = 0
        var hi = blocks.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (blocks[mid].range.start <= offset) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}
