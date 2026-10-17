package dev.dcesares.bifolio.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

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

/** Provides palette and typography for [theme]. Library and reader both use the fixed reading palettes. */
@Composable
public fun BifolioTheme(
    theme: ReadingThemeId = defaultThemeId(),
    content: @Composable () -> Unit,
) {
    val palette = ReadingPalettes.of(theme)
    val typography = remember(palette) { readingTypography(palette) }
    CompositionLocalProvider(LocalPalette provides palette, LocalTypography provides typography, content = content)
}
