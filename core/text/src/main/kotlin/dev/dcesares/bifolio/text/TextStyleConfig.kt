package dev.dcesares.bifolio.text

import java.util.Locale

/**
 * Typographic inputs of [StaticLayoutBlockMeasurer]. Sizes are in px, so the caller applies density and the
 * user's text scale before building it. Fonts are system fonts until M4 (ADR-0009).
 *
 * [charWidthPx] is the width of one body character (`charW` in TYP-04 and TYP-06). [locale] is the document
 * language, else the app locale (TYP-05). [justify] is the user's setting (TYP-06).
 */
public data class TextStyleConfig(
    val bodySizePx: Float = DEFAULT_BODY_SIZE_PX,
    val lineSpacingMultiplier: Float = DEFAULT_LINE_SPACING,
    val charWidthPx: Float = bodySizePx * AVERAGE_CHAR_EM,
    val locale: Locale = Locale.getDefault(),
    val hyphenate: Boolean = true,
    val justify: Boolean = false,
) {
    init {
        require(bodySizePx > 0f && charWidthPx > 0f && lineSpacingMultiplier > 0f) { "sizes must be positive" }
    }

    private companion object {
        const val DEFAULT_BODY_SIZE_PX = 32f
        const val DEFAULT_LINE_SPACING = 1.4f
        const val AVERAGE_CHAR_EM = 0.5f
    }
}
