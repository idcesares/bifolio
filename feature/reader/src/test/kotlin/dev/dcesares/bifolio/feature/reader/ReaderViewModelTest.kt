package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.data.ContentHash
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.data.UriGrants
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException

internal class RecordingGrants : UriGrants {
    val held = mutableSetOf<String>()

    override fun take(uri: String): Boolean = held.add(uri)

    override fun release(uri: String) {
        held -= uri
    }

    override fun isHeld(uri: String): Boolean = uri in held
}

internal class FakeReader(
    private val files: Map<String, RawDocument>,
) : DocumentReader {
    override suspend fun read(uri: String): RawDocument = files[uri] ?: throw IOException("missing $uri")
}

internal fun raw(
    text: String,
    name: String? = "doc.md",
    declaredSize: Long? = null,
) = RawDocument(name, declaredSize ?: text.toByteArray().size.toLong(), text.toByteArray())

internal const val MIB = 1024L * 1024L

/** DOC-01 (octet-stream by name), DOC-02 (grants), DOC-05 (size policy), DOC-03 (notice), ANC-02 (anchor save). */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val grants = RecordingGrants()
    private val repo = RecentsRepository(InMemoryPreferences(), grants)
    private var now = 1_000_000L

    @BeforeEach
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun vm(vararg files: Pair<String, RawDocument>) =
        ReaderViewModel(
            reader = FakeReader(files.toMap()),
            repository = repo,
            preferences = ReadingPreferences(InMemoryPreferences()),
            clock = { now },
            computeDispatcher = Dispatchers.Main,
        )

    private fun ReaderViewModel.ready() = state.value as ReaderUiState.Ready

    @Test
    fun opensAndTitlesFromFirstH1ElseFileName() =
        runTest {
            val v = vm("u1" to raw("# Hello\n\ntext"), "u2" to raw("no heading here", name = "notes.md"))
            v.open(OpenRequest("u1", "text/markdown", persistGrant = false))
            assertEquals("Hello", v.ready().title)
            v.open(OpenRequest("u2", "text/markdown", persistGrant = false))
            assertEquals("notes", v.ready().title)
        }

    @Test
    fun octetStreamIsOpenedOnlyForMarkdownExtensions() =
        runTest {
            val v =
                vm(
                    "bin" to raw("x", name = "data.bin"),
                    "md" to raw("x", name = "README.MD"),
                    "mkd" to raw("x", name = "a.mkd"),
                    "none" to raw("x", name = null),
                    "txt" to raw("x", name = "plain.txt"),
                )
            val octet = "application/octet-stream"
            v.open(OpenRequest("bin", octet, false))
            assertEquals(ReaderUiState.Failed(FailureReason.NotMarkdown), v.state.value)
            v.open(OpenRequest("none", octet, false))
            assertEquals(ReaderUiState.Failed(FailureReason.NotMarkdown), v.state.value)
            v.open(OpenRequest("md", octet, false))
            assertTrue(v.state.value is ReaderUiState.Ready)
            v.open(OpenRequest("mkd", octet, false))
            assertTrue(v.state.value is ReaderUiState.Ready)
            // text/plain is declared Markdown-capable by DOC-01: opens whatever the extension.
            v.open(OpenRequest("txt", "text/plain", false))
            assertTrue(v.state.value is ReaderUiState.Ready)
        }

    @Test
    fun refusesFilesAboveThirtyTwoMiBWithoutRecordingThem() =
        runTest {
            val v = vm("big" to RawDocument("big.md", 32 * MIB + 1, bytes = null))
            v.open(OpenRequest("big", "text/markdown", true))
            assertEquals(ReaderUiState.Failed(FailureReason.TooLarge), v.state.value)
            assertTrue(repo.recents.first().isEmpty())
            assertFalse(grants.isHeld("big"))
        }

    @Test
    fun filesAboveEightMiBOpenWithScrollOnlyNotice() =
        runTest {
            val v = vm("m" to raw("# Big\n\nbody", declaredSize = 8 * MIB + 1))
            v.open(OpenRequest("m", "text/markdown", false))
            assertEquals(setOf(ReaderNotice.ScrollOnlyLargeFile), v.ready().notices)
        }

    @Test
    fun exactlyEightMiBIsNormal() =
        runTest {
            val v = vm("m" to raw("# Edge", declaredSize = 8 * MIB))
            v.open(OpenRequest("m", "text/markdown", false))
            assertTrue(v.ready().notices.isEmpty())
        }

    @Test
    fun windows1252FallbackShowsNotice() =
        runTest {
            val bytes = File("../../testdata/corpus/windows-1252.md").readBytes()
            val v = vm("w" to RawDocument("w.md", bytes.size.toLong(), bytes))
            v.open(OpenRequest("w", "text/markdown", false))
            assertTrue(ReaderNotice.Windows1252Fallback in v.ready().notices)
        }

    @Test
    fun utf8DocumentsHaveNoNotice() =
        runTest {
            val v = vm("u" to raw("acentuação: ção"))
            v.open(OpenRequest("u", "text/markdown", false))
            assertTrue(v.ready().notices.isEmpty())
        }

    @Test
    fun grantIsTakenOnlyWhenRequested() =
        runTest {
            val v = vm("a" to raw("a"), "b" to raw("b"))
            v.open(OpenRequest("a", null, persistGrant = true))
            v.open(OpenRequest("b", null, persistGrant = false))
            assertEquals(setOf("a"), grants.held)
        }

    @Test
    fun unreadableFileFails() =
        runTest {
            val v = vm()
            v.open(OpenRequest("nowhere", null, false))
            assertEquals(ReaderUiState.Failed(FailureReason.Unreadable), v.state.value)
        }

    @Test
    fun openingRecordsARecentAndRestoresTheStoredAnchor() =
        runTest {
            val text = "# One\n\nfirst\n\n## Two\n\nsecond\n\n## Three\n\nthird"
            val v = vm("u" to raw(text, name = "story.md"))
            v.open(OpenRequest("u", null, false))
            val first = v.ready()
            assertEquals(Anchor(0, 0), first.initialAnchor)
            v.onAnchorSettled(Anchor(3, 0))
            v.flush()

            val again = vm("u" to raw(text, name = "story.md"))
            again.open(OpenRequest("u", null, false))
            assertEquals(Anchor(3, 0), again.ready().initialAnchor)

            val recent = repo.recents.first().single()
            assertEquals("One", recent.title)
            assertEquals("story.md", recent.fileName)
            assertEquals(ContentHash.of(text.toByteArray()), recent.contentHash)
            assertTrue(recent.progress > 0f && recent.progress < 1f)
        }

    @Test
    fun recentPreviewIsTheFirstParagraphWithCollapsedWhitespace() =
        runTest {
            val v = vm("u" to raw("# Title\n\nA *quiet*   start,\nwith **two** lines.\n\nSecond paragraph."))
            v.open(OpenRequest("u", null, false))
            assertEquals(
                "A quiet start, with two lines.",
                repo.recents
                    .first()
                    .single()
                    .preview,
            )
        }

    @Test
    fun recentPreviewIsTruncatedTo140CharactersWithAnEllipsis() =
        runTest {
            val v = vm("u" to raw("word ".repeat(100).trim()))
            v.open(OpenRequest("u", null, false))
            val preview =
                repo.recents
                    .first()
                    .single()
                    .preview
            assertEquals(140, preview.length)
            assertTrue(preview.endsWith("…"))
        }

    @Test
    fun recentPreviewIsEmptyWithoutAParagraphAndReopeningClearsUnavailable() =
        runTest {
            val v = vm("u" to raw("# Only a heading"))
            v.open(OpenRequest("u", null, false))
            assertEquals(
                "",
                repo.recents
                    .first()
                    .single()
                    .preview,
            )
            repo.markUnavailable("u")
            v.open(OpenRequest("u", null, false))
            assertEquals(
                false,
                repo.recents
                    .first()
                    .single()
                    .unavailable,
            )
        }

    @Test
    fun anchorSavesAreThrottledToOncePerSecondAndFlushedOnStop() =
        runTest {
            val v = vm("u" to raw("# A\n\none\n\ntwo\n\nthree"))
            v.open(OpenRequest("u", null, false))

            now = 10_000
            v.onAnchorSettled(Anchor(1, 0))
            now = 10_300
            v.onAnchorSettled(Anchor(2, 0))
            assertEquals(Anchor(1, 0), repo.anchorFor("u"))

            v.flush()
            assertEquals(Anchor(2, 0), repo.anchorFor("u"))

            now = 12_000
            v.onAnchorSettled(Anchor(3, 0))
            assertEquals(Anchor(3, 0), repo.anchorFor("u"))
        }

    @Test
    fun staleAnchorFromAnEditedDocumentIsClampedToTheLastBlock() =
        runTest {
            val v = vm("u" to raw("# A\n\nonly two blocks"))
            v.open(OpenRequest("u", null, false))
            repo.saveAnchor("u", Anchor(99, 5))
            v.open(OpenRequest("u", null, false))
            assertEquals(Anchor(1, 0), v.ready().initialAnchor)
        }
}
