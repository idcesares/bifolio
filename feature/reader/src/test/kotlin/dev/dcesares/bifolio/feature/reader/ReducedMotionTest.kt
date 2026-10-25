package dev.dcesares.bifolio.feature.reader

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.dcesares.bifolio.data.TurnChoice
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MOT-07, A11Y-05: system "remove animations" or the in-app switch turn every motion into Fade or None. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReducedMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun systemScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun resolved(override: Boolean?): Boolean {
        var reduced = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides override) {
                reduced = rememberReducedMotion()
            }
        }
        compose.waitForIdle()
        return reduced
    }

    @Test
    fun systemRemoveAnimationsTurnsReducedMotionOn() {
        systemScale(0f)
        assertEquals(true, resolved(null))
    }

    @Test
    fun normalSystemAnimationsLeaveItOff() {
        systemScale(1f)
        assertEquals(false, resolved(null))
    }

    @Test
    fun inAppSwitchWinsOverTheSystemSetting() {
        systemScale(1f)
        assertEquals(true, resolved(true))
    }

    @Test
    fun reducedMotionReplacesSlideWithFadeAndKeepsFadeAndNone() {
        assertEquals(PageTurnStyle.FADE, turnStyleFor(TurnChoice.SLIDE, reducedMotion = true))
        assertEquals(PageTurnStyle.FADE, turnStyleFor(TurnChoice.FADE, reducedMotion = true))
        assertEquals(PageTurnStyle.NONE, turnStyleFor(TurnChoice.NONE, reducedMotion = true))
        assertEquals(PageTurnStyle.SLIDE, turnStyleFor(TurnChoice.SLIDE, reducedMotion = false))
    }
}
