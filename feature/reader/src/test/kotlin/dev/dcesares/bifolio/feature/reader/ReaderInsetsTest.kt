package dev.dcesares.bifolio.feature.reader

import android.graphics.Insets
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-04, LAY-08: the reading surfaces lay out against the display cutout only. System bars overlay the page, so
 * they never shrink it and toggling the chrome never repaginates. Robolectric reports zero insets, so the test
 * dispatches fake ones to the window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReaderInsetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun measure(
        bars: Insets,
        cutout: Insets,
    ): Pair<PaddingValues, PaddingValues> {
        lateinit var page: PaddingValues
        lateinit var chrome: PaddingValues
        compose.setContent {
            page = ReaderInsets.asPaddingValues()
            chrome = ChromeInsets.asPaddingValues()
        }
        compose.runOnUiThread {
            val insets =
                WindowInsets
                    .Builder()
                    .setInsets(WindowInsets.Type.systemBars(), bars)
                    .setInsetsIgnoringVisibility(WindowInsets.Type.systemBars(), bars)
                    .setInsets(WindowInsets.Type.displayCutout(), cutout)
                    .build()
            compose.activity.window.decorView
                .dispatchApplyWindowInsets(insets)
        }
        compose.waitForIdle()
        return page to chrome
    }

    @Test
    fun pageIgnoresBarsAndKeepsCutout() {
        val (page, chrome) = measure(bars = Insets.of(0, 96, 0, 72), cutout = Insets.of(0, 40, 0, 0))
        // Density is 1 in this test, so px equal dp.
        assertEquals(40f, page.calculateTopPadding().value, 0.5f)
        assertEquals(0f, page.calculateBottomPadding().value, 0.5f)
        assertEquals(96f, chrome.calculateTopPadding().value, 0.5f)
    }

    @Test
    fun pageIgnoresBarsWhenThereIsNoCutout() {
        val (page, _) = measure(bars = Insets.of(0, 96, 0, 72), cutout = Insets.NONE)
        assertEquals(0f, page.calculateTopPadding().value, 0.5f)
        assertEquals(0f, page.calculateBottomPadding().value, 0.5f)
    }
}
