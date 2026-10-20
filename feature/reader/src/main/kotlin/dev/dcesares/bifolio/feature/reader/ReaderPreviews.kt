package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser

private const val SAMPLE =
    "# A short walk\n\nThe morning was quiet, and the *river* moved slowly under the bridge. " +
        "She carried a **small notebook** and a pencil.\n\n## Lists\n\n- One\n  - Nested\n- [x] Done\n\n" +
        "| Left | Right |\n|:--|--:|\n| a | b |\n\n> A quote.\n\n```\ncode\n```\n"

@Composable
private fun ReaderPreview(theme: ReadingThemeId = ReadingThemeId.Paper) {
    val document = MarkdownParser.parse(SAMPLE)
    BifolioTheme(theme) {
        ReaderScreen(ReaderUiState.Ready(document, "Sample", "preview", emptySet(), Anchor(0, 0)), {})
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
private fun ReaderPaperPreview() = ReaderPreview()

@Composable
private fun LinkSheetPreview(theme: ReadingThemeId) {
    val link = checkNotNull(parseExternalLink("https://example.org/archive/1894"))
    BifolioTheme(theme) {
        Box(Modifier.fillMaxSize()) {
            ReaderPreview(theme)
            ExternalLinkSheet(ExternalLinkPrompt(link, noApp = false, onOpen = {}, onCancel = {}))
        }
    }
}

// NAV-08: the confirmation as a bottom sheet (compact) and a card (from 840dp), over the page.
@Preview(name = "DM-01 phone link", widthDp = 412, heightDp = 915)
@Preview(name = "DM-03 cover screen link", widthDp = 360, heightDp = 800)
@Preview(name = "DM-04 foldable inner flat link", widthDp = 840, heightDp = 900)
@Preview(name = "DM-08 wide 4:3 foldable link", widthDp = 960, heightDp = 720)
@Preview(name = "DM-09 tablet link", widthDp = 1280, heightDp = 900)
@Preview(name = "DM-10 narrow multi-window link", widthDp = 320, heightDp = 640)
@Composable
private fun LinkSheetPaperPreview() = LinkSheetPreview(ReadingThemeId.Paper)

@Preview(name = "Night link", widthDp = 412, heightDp = 915)
@Composable
private fun LinkSheetNightPreview() = LinkSheetPreview(ReadingThemeId.Night)

@Preview(name = "Night", widthDp = 412, heightDp = 915)
@Composable
private fun ReaderNightPreview() = ReaderPreview(ReadingThemeId.Night)
