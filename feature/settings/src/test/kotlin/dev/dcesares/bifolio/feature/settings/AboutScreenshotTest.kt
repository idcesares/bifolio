package dev.dcesares.bifolio.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI-11: version, license, source, contact and the open source licenses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AboutScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+w412dp-h915dp-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { BifolioTheme(theme) { AboutScreen(versionName = "1.0.0", onBack = {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun aboutPaperPhone() = shot("about-paper-phone")

    @Test
    fun aboutNightFont200() = shot("about-night-font200", ReadingThemeId.Night, fontScale = 2f)
}
