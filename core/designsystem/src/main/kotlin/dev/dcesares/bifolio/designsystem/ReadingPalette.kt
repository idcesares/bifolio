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
    /** Syntax colors for fenced and indented code (SYN-11, ADR-0007). */
    val codeTokens: CodeTokenColors,
)

/**
 * Colors of the token classes of ADR-0007, one set per theme. Each holds 4.5:1 or more on `codeSurface`
 * (DESIGN.md section 2, enforced by ContrastTest). Color only: weight and slant never change, so the glyph
 * advance of highlighted code equals plain code.
 */
@Immutable
public data class CodeTokenColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val function: Color,
    val operator: Color,
    val punctuation: Color,
)

private const val RULE_ALPHA = 0.35f
private const val HIT_ALPHA_LIGHT = 0.14f
private const val HIT_ALPHA_DARK = 0.28f
private const val HIT_FOCUSED_ALPHA = 0.4f

/**
 * Other search hits (UI-08, DESIGN.md section 2): `ink` as a neutral wash, stronger on dark pages where a light
 * wash on black is hardly seen. Not the accent tint of `==mark==`.
 */
public val ReadingPalette.searchHit: Color
    get() = ink.copy(alpha = if (isDark) HIT_ALPHA_DARK else HIT_ALPHA_LIGHT)

/** The current search hit: `accent` wash. Painted with an outline too, so it never relies on color alone. */
public val ReadingPalette.searchHitFocused: Color
    get() = accent.copy(alpha = HIT_FOCUSED_ALPHA)

private fun palette(
    id: ReadingThemeId,
    page: Long,
    ink: Long,
    inkMuted: Long,
    accent: Long,
    link: Long,
    codeSurface: Long,
    tokens: CodeTokenColors,
) = ReadingPalette(
    id = id,
    page = Color(page),
    ink = Color(ink),
    inkMuted = Color(inkMuted),
    accent = Color(accent),
    link = Color(link),
    codeSurface = Color(codeSurface),
    rule = Color(inkMuted).copy(alpha = RULE_ALPHA),
    isDark = id == ReadingThemeId.Dusk || id == ReadingThemeId.Night,
    codeTokens = tokens,
)

private fun tokens(
    keyword: Long,
    string: Long,
    number: Long,
    comment: Long,
    type: Long,
    function: Long,
    operator: Long,
    punctuation: Long,
) = CodeTokenColors(
    Color(keyword),
    Color(string),
    Color(number),
    Color(comment),
    Color(type),
    Color(function),
    Color(operator),
    Color(punctuation),
)

/** Palettes straight from DESIGN.md section 2. */
public object ReadingPalettes {
    public val Paper: ReadingPalette =
        palette(
            ReadingThemeId.Paper,
            0xFFF7F3EA,
            0xFF1F1C17,
            0xFF5E574B,
            0xFF8A4B2A,
            0xFF7A3F22,
            0xFFEDE7DA,
            tokens(0xFF8A2F5C, 0xFF2F6B2A, 0xFF1F5F8A, 0xFF6A6354, 0xFF6B4E00, 0xFF6A3F96, 0xFF7A3F22, 0xFF5E574B),
        )
    public val Sepia: ReadingPalette =
        palette(
            ReadingThemeId.Sepia,
            0xFFEBDDC3,
            0xFF33281B,
            0xFF62523C,
            0xFF76391A,
            0xFF6B3316,
            0xFFE0D0B2,
            tokens(0xFF7A2650, 0xFF2A5E24, 0xFF1B5278, 0xFF5C4C36, 0xFF5E4300, 0xFF5E3585, 0xFF6B3316, 0xFF5A4A34),
        )
    public val Dusk: ReadingPalette =
        palette(
            ReadingThemeId.Dusk,
            0xFF1D1C1A,
            0xFFDCD6CB,
            0xFFA59E92,
            0xFFD9A47C,
            0xFFE2B38F,
            0xFF272522,
            tokens(0xFFE58FB8, 0xFF9BCB85, 0xFF86BDE8, 0xFFA09A8E, 0xFFE0C06A, 0xFFC3A6EE, 0xFFE2B38F, 0xFFB5AEA2),
        )
    public val Night: ReadingPalette =
        palette(
            ReadingThemeId.Night,
            0xFF000000,
            0xFFC8C2B7,
            0xFF8E887E,
            0xFFCF9A72,
            0xFFD8AA86,
            0xFF121110,
            tokens(0xFFE085B0, 0xFF93C47D, 0xFF7FB6E2, 0xFF9A9488, 0xFFD9B960, 0xFFBC9FE8, 0xFFD8AA86, 0xFFADA79B),
        )

    public fun of(id: ReadingThemeId): ReadingPalette =
        when (id) {
            ReadingThemeId.Paper -> Paper
            ReadingThemeId.Sepia -> Sepia
            ReadingThemeId.Dusk -> Dusk
            ReadingThemeId.Night -> Night
        }
}
