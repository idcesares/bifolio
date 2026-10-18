package dev.dcesares.bifolio.text

import java.util.Locale

/**
 * Typographic inputs of [TextLayoutFactory]. Sizes are in px, so the caller applies density and the user's
 * text scale before building it. The body and code faces come from [fonts]: the platform serif and monospace by
 * default, the bundled families when the reader supplies them (ADR-0009).
 *
 * [lineHeight] is a factor of the font size (TYP-03). [charWidthPx] is the width of one body character
 * (`charW` in TYP-04 and TYP-06); use [TextLayoutFactory.bodyCharWidth] to measure it. [locale] is the
 * document language, else the app locale (TYP-05). [justify] is the user's setting (TYP-06).
 */
public data class TextStyleConfig(
    val bodySizePx: Float = DEFAULT_BODY_SIZE_PX,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val charWidthPx: Float = bodySizePx * AVERAGE_CHAR_EM,
    val locale: Locale = Locale.getDefault(),
    val hyphenate: Boolean = true,
    val justify: Boolean = false,
    val fonts: FontSet = SystemFontSet,
) {
    init {
        require(bodySizePx > 0f && charWidthPx > 0f && lineHeight > 0f) { "sizes must be positive" }
    }

    /** Same typography with the measured `charW` of its own body font. */
    public fun withMeasuredCharWidth(): TextStyleConfig = copy(charWidthPx = TextLayoutFactory(this).bodyCharWidth())

    private companion object {
        const val DEFAULT_BODY_SIZE_PX = 32f
        const val DEFAULT_LINE_HEIGHT = 1.5f
        const val AVERAGE_CHAR_EM = 0.5f
    }
}
