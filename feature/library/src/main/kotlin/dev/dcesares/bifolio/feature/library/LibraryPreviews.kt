package dev.dcesares.bifolio.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.ReadingThemeId

private const val PREVIEW_NOW = 1_800_000_000_000L
private const val PREVIEW_HOUR = 3_600_000L

private val previewRecents =
    listOf(
        RecentDocument(
            "content://d/1",
            "A short essay on margins",
            "essay.md",
            PREVIEW_NOW - 2 * PREVIEW_HOUR,
            0.42f,
            "h1",
        ),
        RecentDocument("content://d/2", "Release notes", "CHANGELOG.md", PREVIEW_NOW - 50 * PREVIEW_HOUR, 1f, "h2"),
    )

@Composable
private fun LibraryPreview(
    state: LibraryUiState,
    theme: ReadingThemeId = ReadingThemeId.Paper,
) {
    BifolioTheme(theme) {
        LibraryScreen(
            state,
            PREVIEW_NOW,
            onOpenDocument = {},
            onOpenRecent = {},
            onRemoveRecent = {},
            onDismissNotice = {},
        )
    }
}

// Device matrix of docs/TESTING.md, sizes in dp.
@Preview(name = "DM-01 phone", widthDp = 412, heightDp = 915)
@Preview(name = "DM-03 cover screen", widthDp = 360, heightDp = 800)
@Preview(name = "DM-04 foldable inner flat", widthDp = 840, heightDp = 900)
@Preview(name = "DM-08 wide 4:3 foldable", widthDp = 960, heightDp = 720)
@Preview(name = "DM-09 tablet", widthDp = 1280, heightDp = 900)
@Preview(name = "DM-10 narrow multi-window", widthDp = 320, heightDp = 640)
@Composable
private fun LibraryWithRecentsPreview() = LibraryPreview(LibraryUiState(previewRecents))

@Preview(name = "Empty, Night", widthDp = 412, heightDp = 915)
@Composable
private fun LibraryEmptyNightPreview() = LibraryPreview(LibraryUiState(emptyList()), ReadingThemeId.Night)
