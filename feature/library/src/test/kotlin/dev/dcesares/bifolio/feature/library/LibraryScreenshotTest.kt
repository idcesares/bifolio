package dev.dcesares.bifolio.feature.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NOW = 1_800_000_000_000L
private const val HOUR = 3_600_000L

private val sampleRecents =
    listOf(
        RecentDocument("content://d/1", "A short essay on margins", "essay.md", NOW - 2 * HOUR, 0.42f, "h1"),
        RecentDocument("content://d/2", "Release notes", "CHANGELOG.md", NOW - 6 * 24 * HOUR, 1f, "h2"),
        RecentDocument(
            "content://d/3",
            "Notes from a very long meeting with an overly generous title",
            "n.md",
            NOW - 5 * HOUR,
            0.05f,
            "h3",
        ),
    )

private val previewRecents =
    listOf(
        RecentDocument(
            "content://d/1",
            "A short essay on margins",
            "essay.md",
            NOW - 2 * HOUR,
            0.42f,
            "h1",
            preview = "Margins are the quiet part of a page, and the part a reader notices only when they are wrong…",
        ),
        RecentDocument(
            "content://d/2",
            "Release notes",
            "CHANGELOG.md",
            NOW - 6 * 24 * HOUR,
            1f,
            "h2",
            preview = "Short one.",
        ),
    )

private val unavailableRecents =
    listOf(
        previewRecents[0],
        RecentDocument(
            "content://d/9",
            "Moved away",
            "gone.md",
            NOW - 3 * 24 * HOUR,
            0.6f,
            "h9",
            preview = "This file was moved or deleted after it was last read.",
            unavailable = true,
        ),
    )

/** UI-01 and UI-13: library empty state and recents, in Paper and Night, on a phone and the cover screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LibraryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shot(
        name: String,
        state: LibraryUiState,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        qualifiers: String = "w412dp-h915dp-xhdpi",
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            BifolioTheme(theme) {
                LibraryScreen(
                    state,
                    NOW,
                    onOpenDocument = {},
                    onOpenRecent = {},
                    onRemoveRecent = {},
                    onDismissNotice = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun emptyPaperPhone() = shot("library-empty-paper-phone", LibraryUiState(recents = emptyList()))

    @Test
    fun emptyNightPhone() = shot("library-empty-night-phone", LibraryUiState(emptyList()), ReadingThemeId.Night)

    @Test
    fun recentsPaperPhone() = shot("library-recents-paper-phone", LibraryUiState(sampleRecents))

    @Test
    fun recentsNightCover() =
        shot("library-recents-night-cover", LibraryUiState(sampleRecents), ReadingThemeId.Night, "w360dp-h800dp-xhdpi")

    @Test
    fun recentsPaperFontScale200() =
        shot("library-recents-paper-font200", LibraryUiState(sampleRecents), fontScale = 2f)

    @Test
    fun unreadableNoticePaperPhone() =
        shot("library-notice-paper-phone", LibraryUiState(sampleRecents, LibraryNotice.Unreadable))

    @Test
    fun previewPaperPhone() = shot("library-preview-paper-phone", LibraryUiState(previewRecents))

    @Test
    fun unavailablePaperPhone() = shot("library-unavailable-paper-phone", LibraryUiState(unavailableRecents))
}
