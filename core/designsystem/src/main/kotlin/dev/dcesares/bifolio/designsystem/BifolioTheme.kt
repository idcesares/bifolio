package dev.dcesares.bifolio.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

private val LocalPalette = staticCompositionLocalOf { ReadingPalettes.Paper }
private val LocalTypography = staticCompositionLocalOf { readingTypography(ReadingPalettes.Paper) }

/** Access to the current theme tokens. */
public object BifolioTheme {
    public val palette: ReadingPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPalette.current

    public val typography: ReadingTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalTypography.current
}

/** Default theme id when the user has not chosen one: Night in dark mode, Paper otherwise. */
@Composable
@ReadOnlyComposable
public fun defaultThemeId(): ReadingThemeId = if (isSystemInDarkTheme()) ReadingThemeId.Night else ReadingThemeId.Paper

/**
 * Provides palette and typography for [theme]. Library and reader use the fixed reading palettes unless the
 * library passes a [paletteOverride] from [DynamicPalette] (TYP-09).
 */
@Composable
public fun BifolioTheme(
    theme: ReadingThemeId = defaultThemeId(),
    bodySp: Float = DEFAULT_BODY_SP,
    lineHeight: Float = DEFAULT_LINE_HEIGHT,
    font: ReadingFont = ReadingFont.Literata,
    paletteOverride: ReadingPalette? = null,
    content: @Composable () -> Unit,
) {
    val palette = paletteOverride ?: ReadingPalettes.of(theme)
    val assets = LocalContext.current.assets
    val typography =
        remember(palette, bodySp, lineHeight, font) {
            readingTypography(palette, bodySp.sp, lineHeight, font.family(assets), monoFamily(assets))
        }
    val indication = remember(palette) { FocusRingIndication(ring = palette.accent, press = palette.ink) }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTypography provides typography,
        LocalIndication provides indication,
        content = content,
    )
}

private const val DEFAULT_BODY_SP = 18f
private const val DEFAULT_LINE_HEIGHT = 1.5f
