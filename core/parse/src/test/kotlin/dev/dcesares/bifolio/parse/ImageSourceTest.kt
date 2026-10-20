package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.parse.ImageSource.ContentUri
import dev.dcesares.bifolio.parse.ImageSource.DataUri
import dev.dcesares.bifolio.parse.ImageSource.Rejected
import dev.dcesares.bifolio.parse.ImageSource.Relative
import dev.dcesares.bifolio.parse.ImageSource.Remote
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

/** SYN-08 destination rules, PRV-01 (nothing remote is loadable), PRV-03. Pure strings, no I/O. */
class ImageSourceTest {
    private fun classify(
        d: String,
        base: List<String> = emptyList(),
    ) = ImageSources.classify(d, base)

    private fun rejected(
        d: String,
        reason: RejectReason,
        base: List<String> = emptyList(),
    ) = assertEquals(Rejected(reason), classify(d, base), "destination: $d")

    private fun relative(
        d: String,
        vararg segments: String,
        base: List<String> = emptyList(),
    ) = assertEquals(Relative(segments.toList()), classify(d, base), "destination: $d")

    @Test
    fun `relative paths are normalized into segments`() {
        relative("a.png", "a.png")
        relative("img/a.png", "img", "a.png")
        relative("./img/./a.png", "img", "a.png")
        relative("img//a.png", "img", "a.png")
        relative("img/sub/../a.png", "img", "a.png")
        relative("  img/a.png \n", "img", "a.png")
        assertEquals("img/a.png", (classify("img/a.png") as Relative).path)
    }

    @Test
    fun `query and fragment are cut before decoding`() {
        relative("a.png?size=2#top", "a.png")
        relative("a.png#frag", "a.png")
        relative("a%3Fb.png", "a?b.png")
        rejected("?x=1", RejectReason.EMPTY)
    }

    @Test
    fun `percent escapes are decoded once as UTF-8`() {
        relative("with%20space.png", "with space.png")
        relative("caf%C3%A9.png", "café.png")
        relative("日本語/ção.png", "日本語", "ção.png")
        rejected("bad%zz.png", RejectReason.BAD_ENCODING)
        rejected("trunc%2", RejectReason.BAD_ENCODING)
        rejected("lone%FF.png", RejectReason.BAD_ENCODING)
    }

    @Test
    fun `a path may not climb above the granted root`() {
        rejected("../a.png", RejectReason.TRAVERSAL)
        rejected("img/../../a.png", RejectReason.TRAVERSAL)
        rejected("a/b/../../../c.png", RejectReason.TRAVERSAL)
        relative("a/b/../../c.png", "c.png")
    }

    @Test
    fun `encoded dots and slashes are decoded before the climb check`() {
        rejected("%2e%2e/a.png", RejectReason.TRAVERSAL)
        rejected("%2E%2E%2Fa.png", RejectReason.TRAVERSAL)
        rejected("img/%2e%2e/%2e%2e/a.png", RejectReason.TRAVERSAL)
        rejected("..%2fa.png", RejectReason.TRAVERSAL)
        rejected("a%2f..%2f..%2fb.png", RejectReason.TRAVERSAL)
    }

    @Test
    fun `double encoding is refused instead of being decoded twice`() {
        rejected("%252e%252e/a.png", RejectReason.BAD_ENCODING)
        rejected("a%2500.png", RejectReason.BAD_ENCODING)
    }

    @Test
    fun `the base folder lets a document in a subfolder reach its siblings but not the root's parent`() {
        relative("../img/a.png", "img", "a.png", base = listOf("docs"))
        relative("a.png", "docs", "a.png", base = listOf("docs"))
        rejected("../../a.png", RejectReason.TRAVERSAL, base = listOf("docs"))
        rejected("../a.png", RejectReason.TRAVERSAL)
    }

    @Test
    fun `absolute paths, drive letters and backslashes are refused`() {
        rejected("/etc/passwd", RejectReason.ABSOLUTE_PATH)
        rejected("%2Fetc%2Fpasswd", RejectReason.ABSOLUTE_PATH)
        rejected("C:\\Windows\\a.png", RejectReason.SCHEME)
        rejected("C:/Windows/a.png", RejectReason.SCHEME)
        rejected("\\\\server\\share\\a.png", RejectReason.BACKSLASH)
        rejected("img\\..\\..\\a.png", RejectReason.BACKSLASH)
        rejected("img%5Ca.png", RejectReason.BACKSLASH)
        rejected("a.png:stream", RejectReason.SCHEME)
        rejected("javascript%3Aalert(1)", RejectReason.SCHEME)
    }

    @Test
    fun `NUL and control characters are refused raw and encoded`() {
        rejected("a\u0000.png", RejectReason.CONTROL_CHARACTER)
        rejected("a%00.png", RejectReason.CONTROL_CHARACTER)
        rejected("a%0A.png", RejectReason.CONTROL_CHARACTER)
        rejected("a\tb.png", RejectReason.CONTROL_CHARACTER)
        rejected("java\tscript:alert(1)", RejectReason.CONTROL_CHARACTER)
        rejected("a%7F.png", RejectReason.CONTROL_CHARACTER)
    }

    @Test
    fun `a directory or an empty destination is no file`() {
        rejected("", RejectReason.EMPTY)
        rejected("   ", RejectReason.EMPTY)
        rejected("img/", RejectReason.NOT_A_FILE)
        rejected(".", RejectReason.NOT_A_FILE)
        rejected("img/..", RejectReason.NOT_A_FILE)
    }

    @Test
    fun `every scheme except data is refused and http is remote`() {
        rejected("file:///sdcard/a.png", RejectReason.SCHEME)
        rejected("FILE:///sdcard/a.png", RejectReason.SCHEME)
        rejected("javascript:alert(1)", RejectReason.SCHEME)
        rejected("JavaScript:alert(1)", RejectReason.SCHEME)
        rejected("android_asset:a.png", RejectReason.SCHEME)
        rejected("android.resource://x/1", RejectReason.SCHEME)
        rejected("ftp://host/a.png", RejectReason.SCHEME)
        rejected("vbscript:x", RejectReason.SCHEME)
        assertEquals(Remote("http://example.com/a.png"), classify("http://example.com/a.png"))
        assertEquals(Remote("HTTPS://example.com/a.png"), classify("HTTPS://example.com/a.png"))
        assertEquals(Remote("//example.com/a.png"), classify("//example.com/a.png"))
    }

    @Test
    fun `android_asset as a folder name is an ordinary relative path`() {
        relative("android_asset/a.png", "android_asset", "a.png")
    }

    @Test
    fun `content URIs in document text are refused unless the caller opts in`() {
        rejected("content://com.example/doc/1", RejectReason.CONTENT_URI)
        assertEquals(
            ContentUri("content://com.example/doc/1"),
            ImageSources.classify("content://com.example/doc/1", allowContentUri = true),
        )
        assertEquals(
            Rejected(RejectReason.SCHEME),
            ImageSources.classify("file:///a.png", allowContentUri = true),
        )
    }

    // Data URIs.

    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private fun dataUri(
        mime: String = "image/png",
        bytes: ByteArray = pngBytes,
    ) = "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `a base64 image data URI decodes back to its bytes`() {
        for (mime in listOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp", "IMAGE/PNG")) {
            val source = classify(dataUri(mime)) as DataUri
            assertEquals(mime.lowercase(), source.mimeType)
            assertEquals(pngBytes.size, source.decodedSize)
            assertArrayEquals(pngBytes, source.decode())
        }
    }

    @Test
    fun `parameters before base64 are allowed and the size comes from the payload length`() {
        for (n in 1..9) {
            val bytes = ByteArray(n) { it.toByte() }
            val source = classify("data:image/png;name=x.png;base64," + Base64.getEncoder().encodeToString(bytes))
            assertEquals(n, (source as DataUri).decodedSize, "n=$n")
            val payload = Base64.getEncoder().withoutPadding().encodeToString(bytes)
            val unpadded = classify("data:image/png;base64,$payload")
            assertArrayEquals(bytes, (unpadded as DataUri).decode(), "unpadded n=$n")
        }
    }

    @Test
    fun `svg and other non raster types are refused`() {
        rejected("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=", RejectReason.DATA_NOT_IMAGE)
        rejected("data:image/svg+xml;utf8,<svg></svg>", RejectReason.DATA_NOT_IMAGE)
        rejected("data:text/html;base64,PGI+eDwvYj4=", RejectReason.DATA_NOT_IMAGE)
        rejected("data:application/octet-stream;base64,AAAA", RejectReason.DATA_NOT_IMAGE)
        rejected("data:;base64,AAAA", RejectReason.DATA_NOT_IMAGE)
        rejected("data:image/tiff;base64,AAAA", RejectReason.DATA_NOT_IMAGE)
    }

    @Test
    fun `malformed data URIs are refused`() {
        rejected("data:image/png;base64", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,", RejectReason.DATA_MALFORMED)
        rejected("data:image/png,AAAA", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;charset=utf-8,AAAA", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,AA*A", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,AA=A", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,A===", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,AAAAA", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,AA A", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,AAA=AAAA", RejectReason.DATA_MALFORMED)
        rejected("data:image/png;base64,AAA-_A", RejectReason.DATA_MALFORMED)
    }

    @Test
    fun `size is checked on the encoded length before any decoding`() {
        val limit = ImageSources.MAX_DATA_URI_CHARS
        assertEquals(2 * 1024 * 1024, limit)
        val prefix = "data:image/png;base64,"
        val atLimit = prefix + "A".repeat(limit - prefix.length)
        assertEquals(limit, atLimit.length)
        assertTrue(classify(atLimit) is DataUri)
        // Not even valid base64: the length rule fires first, so no scan or decode of 2 MB of junk happens.
        rejected(prefix + "!".repeat(limit - prefix.length + 1), RejectReason.DATA_TOO_LARGE)
        rejected(atLimit + "A", RejectReason.DATA_TOO_LARGE)
    }

    @Test
    fun `an unknown scheme case is not confused with data`() {
        rejected("datax:image/png;base64,AAAA", RejectReason.SCHEME)
        assertTrue(classify("DATA:image/png;base64,AAAA") is DataUri)
    }
}
