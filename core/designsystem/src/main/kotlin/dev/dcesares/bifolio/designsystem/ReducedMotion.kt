package dev.dcesares.bifolio.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Forces the reduced-motion decision, for tests and previews. `null` follows the system setting. */
public val LocalReducedMotionOverride: ProvidableCompositionLocal<Boolean?> = compositionLocalOf { null }

/**
 * True when the user turned animations off (MOT-07). Reading surfaces then jump instead of animating.
 * The system "remove animations" switch sets the animator duration scale to zero.
 */
@Composable
public fun rememberReducedMotion(): Boolean {
    val override = LocalReducedMotionOverride.current
    val resolver = LocalContext.current.contentResolver
    if (override != null) return override
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
