package dev.dcesares.bifolio.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private const val PHONE = "w412dp-h915dp-xhdpi"
private const val COVER = "w360dp-h800dp-xhdpi"
private const val TABLET = "w1280dp-h900dp-xhdpi"

private fun corpus(name: String): ReaderUiState.Ready {
    val text = File("../../testdata/corpus/$name.md").readText().replace("\r\n", "\n")
    return ReaderUiState.Ready(MarkdownParser.parse(text), name, "hash-$name", emptySet(), Anchor(0, 0))
}

/** UI-13 reading surface in scroll mode, corpus C-01, C-03, C-05 and C-11 (LAY-03, LAY-05, SYN-01..07). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReaderScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        qualifiers: String = PHONE,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent { BifolioTheme(theme) { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun doc(
        name: String,
        shotName: String,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        qualifiers: String = PHONE,
        fontScale: Float = 1f,
        notices: Set<ReaderNotice> = emptySet(),
    ) {
        val ready = corpus(name)
        shot(shotName, theme, qualifiers, fontScale) {
            ReaderScreen(ReaderUiState.Ready(ready.document, ready.title, ready.contentHash, notices, Anchor(0, 0)), {})
        }
    }

    @Test
    fun proseShortPaperPhone() = doc("prose-short", "reader-c01-prose-paper-phone")

    @Test
    fun proseShortNightCover() = doc("prose-short", "reader-c01-prose-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun proseShortSepiaTablet() = doc("prose-short", "reader-c01-prose-sepia-tablet", ReadingThemeId.Sepia, TABLET)

    @Test
    fun headingsPaperPhone() = doc("headings-all-levels", "reader-c03-headings-paper-phone")

    @Test
    fun headingsPaperFont200() = doc("headings-all-levels", "reader-c03-headings-paper-font200", fontScale = 2f)

    @Test
    fun listsNestedPaperPhone() = doc("lists-nested", "reader-c05-lists-paper-phone")

    @Test
    fun listsNestedNightCover() = doc("lists-nested", "reader-c05-lists-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun tasksPaperPhone() = doc("tasks", "reader-tasks-paper-phone")

    /** SYN-13: the metadata card on the scroll surface. */
    @Test
    fun frontMatterPaperPhone() = doc("front-matter", "reader-front-matter-paper-phone")

    @Test
    fun tablesSimplePaperPhone() = doc("tables-simple", "reader-c11-tables-paper-phone")

    @Test
    fun tablesSimpleNightCover() = doc("tables-simple", "reader-c11-tables-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun tablesSimplePaperFont200() = doc("tables-simple", "reader-c11-tables-paper-font200", fontScale = 2f)

    @Test
    fun quotesAndCodePaperPhone() = doc("quotes-and-callouts", "reader-quotes-paper-phone")

    @Test
    fun codeLanguagesNightPhone() = doc("code-languages", "reader-code-night-phone", ReadingThemeId.Night)

    @Test
    fun noticesPaperPhone() =
        doc(
            "prose-short",
            "reader-notices-paper-phone",
            notices = setOf(ReaderNotice.ScrollOnlyLargeFile, ReaderNotice.Windows1252Fallback),
        )

    @Test
    fun failureNotMarkdownPaperPhone() =
        shot("reader-failed-paper-phone") { ReaderFailure(FailureReason.NotMarkdown, onBack = {}) }
}
