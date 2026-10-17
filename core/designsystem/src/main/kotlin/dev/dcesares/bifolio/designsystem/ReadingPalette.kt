package dev.dcesares.bifolio.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The four fixed reading themes (TYP-08, DESIGN.md section 2). */
public enum class ReadingThemeId { Paper, Sepia, Dusk, Night }

/** Colors of one reading theme. Fixed palettes: dynamic color never applies to the reading surface. */
@Immutable
public data class ReadingPalette(
    val id: ReadingThemeId,
    val page: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val link: Color,
    val codeSurface: Color,
    /** Hairlines and quote bars. Derived: `inkMuted` at low alpha. */
    val rule: Color,
    val isDark: Boolean,
)

private const val RULE_ALPHA = 0.35f

private fun palette(
    id: ReadingThemeId,
    page: Long,
    ink: Long,
    inkMuted: Long,
    accent: Long,
    link: Long,
    codeSurface: Long,
    isDark: Boolean,
) = ReadingPalette(
    id = id,
    page = Color(page),
    ink = Color(ink),
    inkMuted = Color(inkMuted),
    accent = Color(accent),
    link = Color(link),
    codeSurface = Color(codeSurface),
    rule = Color(inkMuted).copy(alpha = RULE_ALPHA),
    isDark = isDark,
)

/** Palettes straight from DESIGN.md section 2. */
public object ReadingPalettes {
    public val Paper: ReadingPalette =
        palette(ReadingThemeId.Paper, 0xFFF7F3EA, 0xFF1F1C17, 0xFF5E574B, 0xFF8A4B2A, 0xFF7A3F22, 0xFFEDE7DA, false)
    public val Sepia: ReadingPalette =
        palette(ReadingThemeId.Sepia, 0xFFEBDDC3, 0xFF33281B, 0xFF62523C, 0xFF76391A, 0xFF6B3316, 0xFFE0D0B2, false)
    public val Dusk: ReadingPalette =
        palette(ReadingThemeId.Dusk, 0xFF1D1C1A, 0xFFDCD6CB, 0xFFA59E92, 0xFFD9A47C, 0xFFE2B38F, 0xFF272522, true)
    public val Night: ReadingPalette =
        palette(ReadingThemeId.Night, 0xFF000000, 0xFFC8C2B7, 0xFF8E887E, 0xFFCF9A72, 0xFFD8AA86, 0xFF121110, true)

    public fun of(id: ReadingThemeId): ReadingPalette =
        when (id) {
            ReadingThemeId.Paper -> Paper
            ReadingThemeId.Sepia -> Sepia
            ReadingThemeId.Dusk -> Dusk
            ReadingThemeId.Night -> Night
        }
}
