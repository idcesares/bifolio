package dev.dcesares.bifolio.data

import dev.dcesares.bifolio.model.Anchor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CodecAndHashTest {
    @Test
    fun recentsRoundTripWithAwkwardText() {
        val awkward =
            RecentDocument(
                uri = "content://p/doc%20x?a=1&b=2",
                title = "Tab\there\nand newline, acento: ação, 日本語 + plus %41",
                fileName = "a b+c.md",
                lastOpenedMillis = 1_700_000_000_123,
                progress = 0.5f,
                contentHash = "abc",
            )
        val list = listOf(awkward, awkward.copy(uri = "content://p/2", title = ""))
        assertEquals(list, RecentsCodec.decodeRecents(RecentsCodec.encodeRecents(list)))
    }

    @Test
    fun malformedRecordsAreSkippedNotFatal() {
        val good = RecentDocument("content://p/1", "T", "f.md", 1, 0.1f, "h")
        val text = RecentsCodec.encodeRecents(listOf(good)) + "\nthis is not a record\n\t\t\n"
        assertEquals(listOf(good), RecentsCodec.decodeRecents(text))
    }

    @Test
    fun progressIsClampedWhenDecoding() {
        val bad = RecentsCodec.encodeRecents(listOf(RecentDocument("u", "t", "f", 1, 0.5f, "h"))).replace("0.5", "7.5")
        assertEquals(1f, RecentsCodec.decodeRecents(bad).single().progress)
    }

    @Test
    fun anchorsRoundTripPreservingOrder() {
        val entries = listOf("h2" to Anchor(2, 5), "h1" to Anchor(0, 0))
        assertEquals(entries, RecentsCodec.decodeAnchors(RecentsCodec.encodeAnchors(entries)))
    }

    @Test
    fun contentHashIsSha256HexAndContentSensitive() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ContentHash.of(ByteArray(0)),
        )
        assertNotEquals(ContentHash.of("a".toByteArray()), ContentHash.of("b".toByteArray()))
    }

    @Test
    fun previewAndUnavailableRoundTrip() {
        val list =
            listOf(
                RecentDocument(
                    "u1",
                    "t",
                    "f",
                    1,
                    0.5f,
                    "h",
                    preview = "Tab\there, 100% \u00e9 & more\nline",
                    unavailable = true,
                ),
                RecentDocument("u2", "t", "f", 2, 0f, "h"),
            )
        assertEquals(list, RecentsCodec.decodeRecents(RecentsCodec.encodeRecents(list)))
    }

    @Test
    fun entriesStoredBeforePreviewExistedDecodeWithDefaults() {
        val old = "u%3A1	Title	file.md	42	0.25	hash"
        val r = RecentsCodec.decodeRecents(old).single()
        assertEquals(RecentDocument("u:1", "Title", "file.md", 42, 0.25f, "hash", preview = "", unavailable = false), r)
    }
}
