package dev.dcesares.bifolio.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale of DESIGN.md section 3, every size a multiple of the body size.
 * Families are system placeholders until M4 bundles Literata and JetBrains Mono (OPN-03, ADR-0009).
 */
@Immutable
public class ReadingTypography internal constructor(
    public val bodySize: TextUnit,
    public val body: TextStyle,
    public val headings: List<TextStyle>,
    public val code: TextStyle,
    public val caption: TextStyle,
    /** Vertical gap between paragraphs: 0.6 lines (DESIGN.md section 3), as a multiple of the body size. */
    public val paragraphGapEm: Float,
) {
    public fun heading(level: Int): TextStyle = headings[(level - 1).coerceIn(0, headings.lastIndex)]
}

private const val BODY_LINE_HEIGHT = 1.5f
private const val CODE_SCALE = 0.88f
private const val CODE_LINE_HEIGHT = 1.45f
private const val CAPTION_SCALE = 0.8f
private const val PARAGRAPH_GAP_LINES = 0.6f
private const val H5_TRACKING = 0.04f
private const val H5_INDEX = 4
private const val H6_INDEX = 5

private data class HeadingSpec(
    val scale: Float,
    val lineHeight: Float,
    val weight: FontWeight,
)

private val headingSpecs =
    listOf(
        HeadingSpec(1.75f, 1.2f, FontWeight.SemiBold),
        HeadingSpec(1.45f, 1.25f, FontWeight.SemiBold),
        HeadingSpec(1.22f, 1.3f, FontWeight.SemiBold),
        HeadingSpec(1.08f, 1.35f, FontWeight.Bold),
        HeadingSpec(1.0f, 1.4f, FontWeight.Bold),
        HeadingSpec(0.92f, 1.4f, FontWeight.Bold),
    )

/** Builds the reading type scale for [bodySize] using [bodyFamily] and [codeFamily]. */
public fun readingTypography(
    palette: ReadingPalette,
    bodySize: TextUnit = 18.sp,
    bodyFamily: FontFamily = FontFamily.Serif,
    codeFamily: FontFamily = FontFamily.Monospace,
): ReadingTypography {
    val size = bodySize.value
    val body =
        TextStyle(
            fontFamily = bodyFamily,
            fontSize = bodySize,
            lineHeight = (size * BODY_LINE_HEIGHT).sp,
            color = palette.ink,
        )
    val headings =
        headingSpecs.mapIndexed { index, spec ->
            body.copy(
                fontSize = (size * spec.scale).sp,
                lineHeight = (size * spec.scale * spec.lineHeight).sp,
                fontWeight = spec.weight,
                color = if (index == H6_INDEX) palette.inkMuted else palette.ink,
                letterSpacing = if (index == H5_INDEX) H5_TRACKING.em else TextUnit.Unspecified,
                fontFeatureSettings = if (index == H5_INDEX) "smcp" else null,
            )
        }
    return ReadingTypography(
        bodySize = bodySize,
        body = body,
        headings = headings,
        code =
            body.copy(
                fontFamily = codeFamily,
                fontSize = (size * CODE_SCALE).sp,
                lineHeight = (size * CODE_SCALE * CODE_LINE_HEIGHT).sp,
            ),
        caption = body.copy(fontSize = (size * CAPTION_SCALE).sp, color = palette.inkMuted),
        paragraphGapEm = PARAGRAPH_GAP_LINES * BODY_LINE_HEIGHT,
    )
}
