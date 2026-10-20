package dev.dcesares.bifolio.feature.reader

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PHONE = "w412dp-h915dp-xhdpi"
private const val TABLET = "w1280dp-h900dp-xhdpi"
private const val ARCHIVE = "https://example.org/archive"
private const val LONG_URL =
    "https://archive.example.org/harbour/ledgers/1894/volume-3/entries?page=2&sort=date&q=" +
        "the+first+ledger+of+the+harbour+master+and+the+river+wardens#entry-1894-03-117"

/** NAV-08: the confirmation before an external link opens, as a bottom sheet and as a card beside the page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ExternalLinkScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val ready: ReaderUiState.Ready by lazy {
        val text = File("../../testdata/corpus/prose-short.md").readText().replace("\r\n", "\n")
        ReaderUiState.Ready(MarkdownParser.parse(text), "prose", "hash-prose", emptySet(), Anchor(0, 0))
    }

    private fun show(
        url: String,
        qualifiers: String,
        theme: ReadingThemeId,
        fontScale: Float = 1f,
        noApp: Boolean = false,
        opened: MutableList<Unit> = mutableListOf(),
        cancelled: MutableList<Unit> = mutableListOf(),
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        val link = checkNotNull(parseExternalLink(url))
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    Box(Modifier.fillMaxSize()) {
                        ReaderScreen(ready, {})
                        ExternalLinkSheet(
                            ExternalLinkPrompt(
                                link,
                                noApp,
                                onOpen = { opened += Unit },
                                onCancel = { cancelled += Unit },
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    @Test
    fun sheetPaperPhone() {
        show(ARCHIVE, PHONE, ReadingThemeId.Paper)
        shot("link-sheet-paper-phone")
    }

    @Test
    fun sheetNightPhone() {
        show(ARCHIVE, PHONE, ReadingThemeId.Night)
        shot("link-sheet-night-phone")
    }

    @Test
    fun cardPaperTablet() {
        show(ARCHIVE, TABLET, ReadingThemeId.Paper)
        shot("link-card-paper-tablet")
    }

    @Test
    fun cardNightTablet() {
        show(ARCHIVE, TABLET, ReadingThemeId.Night)
        shot("link-card-night-tablet")
    }

    @Test
    fun longUrlWrapsAndKeepsItsHostAtFont200() {
        show(LONG_URL, PHONE, ReadingThemeId.Paper, fontScale = 2f)
        shot("link-sheet-long-paper-phone-font200")
    }

    @Test
    fun lookAlikeAddressesAreCalledOut() {
        show("https://good.example.com@evil.example.net/login", PHONE, ReadingThemeId.Paper)
        shot("link-sheet-userinfo-paper-phone")
    }

    @Test
    fun noAppMessageAppearsAfterAFailedOpen() {
        show(ARCHIVE, PHONE, ReadingThemeId.Paper, noApp = true)
        compose.onNodeWithTag(LINK_NO_APP_TAG).assertIsDisplayed()
        shot("link-sheet-no-app-paper-phone")
    }

    @Test
    fun showsTheFullUrlWithOpenAndCancel() {
        show(ARCHIVE, PHONE, ReadingThemeId.Paper)
        compose.onNodeWithTag(LINK_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LINK_URL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LINK_OPEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LINK_CANCEL_TAG).assertIsDisplayed()
    }

    @Test
    fun openAndCancelReportTheirIntent() {
        val opened = mutableListOf<Unit>()
        val cancelled = mutableListOf<Unit>()
        show(ARCHIVE, PHONE, ReadingThemeId.Paper, opened = opened, cancelled = cancelled)
        compose.onNodeWithTag(LINK_OPEN_TAG).performClick()
        compose.onNodeWithTag(LINK_CANCEL_TAG).performClick()
        assertEquals(1, opened.size)
        assertEquals(1, cancelled.size)
    }

    @Test
    fun escapeCancels() {
        val cancelled = mutableListOf<Unit>()
        show(ARCHIVE, PHONE, ReadingThemeId.Paper, cancelled = cancelled)
        compose.onNodeWithTag(LINK_SHEET_TAG).performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, cancelled.size)
    }

    @Test
    fun openingSendsOneViewIntentForThatUrlAndNothingElse() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val link = checkNotNull(parseExternalLink(ARCHIVE))
        assertTrue(context.openExternalLink(link))
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(ARCHIVE, started.dataString)
        assertTrue(started.categories.contains(Intent.CATEGORY_BROWSABLE))
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test
    fun noAppToOpenItIsReportedNotThrown() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val link = checkNotNull(parseExternalLink("mailto:reader@example.org"))
        shadowOf(context).checkActivities(true)
        assertFalse(context.openExternalLink(link))
    }
}
