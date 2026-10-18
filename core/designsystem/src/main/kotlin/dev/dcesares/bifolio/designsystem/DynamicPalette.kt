package dev.dcesares.bifolio.designsystem

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Palette from the system tonal colors (Android 12+), for library and settings chrome only (TYP-09). The reading
 * surface always uses the fixed palettes, so this never reaches a page. Follows the system dark setting.
 */
public object DynamicPalette {
    /** Whether this Android version offers system tonal colors. */
    public val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** The palette for [context], or null before Android 12. */
    public fun of(
        context: Context,
        dark: Boolean,
    ): ReadingPalette? {
        if (!isAvailable) return null

        fun tone(id: Int) = Color(context.getColor(id))
        val muted = tone(if (dark) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700)
        return ReadingPalette(
            id = if (dark) ReadingThemeId.Night else ReadingThemeId.Paper,
            page = tone(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50),
            ink = tone(if (dark) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900),
            inkMuted = muted,
            accent = tone(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_700),
            link = tone(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_800),
            codeSurface = tone(if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_100),
            rule = muted.copy(alpha = RULE_ALPHA),
            isDark = dark,
        )
    }

    private const val RULE_ALPHA = 0.35f
}

/** The dynamic palette for the current system dark setting, or null before Android 12 (TYP-09). */
@Composable
public fun rememberDynamicPalette(): ReadingPalette? {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    return remember(context, dark) { DynamicPalette.of(context, dark) }
}
