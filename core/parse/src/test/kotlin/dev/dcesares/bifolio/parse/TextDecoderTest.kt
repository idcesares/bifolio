package dev.dcesares.bifolio.parse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextDecoderTest {
    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test
    fun `DOC-03 decodes UTF-8 without BOM`() {
        val r = TextDecoder.decode(utf8("ação €"))
        assertEquals("ação €", r.text)
        assertEquals(SourceEncoding.UTF_8, r.encoding)
        assertFalse(r.encodingWarning)
    }

    @Test
    fun `DOC-03 decodes UTF-8 with BOM and strips it`() {
        val r = TextDecoder.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + utf8("olá"))
        assertEquals("olá", r.text)
        assertEquals(SourceEncoding.UTF_8_BOM, r.encoding)
        assertFalse(r.encodingWarning)
    }

    @Test
    fun `DOC-03 detects UTF-16 little endian by BOM`() {
        val r = TextDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a€b".toByteArray(Charsets.UTF_16LE))
        assertEquals("a€b", r.text)
        assertEquals(SourceEncoding.UTF_16_LE, r.encoding)
        assertFalse(r.encodingWarning)
    }

    @Test
    fun `DOC-03 detects UTF-16 big endian by BOM`() {
        val r = TextDecoder.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "a€b".toByteArray(Charsets.UTF_16BE))
        assertEquals("a€b", r.text)
        assertEquals(SourceEncoding.UTF_16_BE, r.encoding)
    }

    @Test
    fun `DOC-03 falls back to Windows-1252 with a warning when bytes are not UTF-8`() {
        val r =
            TextDecoder.decode(
                byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte(), 0x80.toByte()),
            )
        assertEquals("café€", r.text)
        assertEquals(SourceEncoding.WINDOWS_1252, r.encoding)
        assertTrue(r.encodingWarning)
    }

    @Test
    fun `DOC-03 empty input is UTF-8 with no warning`() {
        val r = TextDecoder.decode(ByteArray(0))
        assertEquals("", r.text)
        assertFalse(r.encodingWarning)
    }

    @Test
    fun `DOC-03 corpus C-25 UTF-16 file decodes`() {
        val r = TextDecoder.decode(Corpus.bytes("utf16-bom.md"))
        assertEquals(SourceEncoding.UTF_16_LE, r.encoding)
        assertTrue(r.text.startsWith("# Codificação UTF-16"))
        assertTrue(r.text.contains("日本語"))
    }

    @Test
    fun `DOC-03 corpus C-26 Windows-1252 file decodes with warning`() {
        val r = TextDecoder.decode(Corpus.bytes("windows-1252.md"))
        assertEquals(SourceEncoding.WINDOWS_1252, r.encoding)
        assertTrue(r.encodingWarning)
        assertTrue(r.text.contains("Café, ação, “aspas curvas”, € 10"))
    }

    @Test
    fun `DOC-04 CRLF and CR become LF and nothing else changes`() {
        assertEquals("a\nb\nc\n\nd", TextDecoder.normalizeLineEndings("a\r\nb\rc\r\n\r\nd"))
        assertEquals("  x\t\n y ", TextDecoder.normalizeLineEndings("  x\t\r\n y "))
    }

    @Test
    fun `DOC-04 text without CR is returned unchanged`() {
        val s = "a\nb"
        assertTrue(s === TextDecoder.normalizeLineEndings(s))
    }

    @Test
    fun `DOC-04 corpus C-24 file really has CRLF and loads with LF only`() {
        val raw = Corpus.bytes("crlf.md")
        assertTrue(String(raw, Charsets.UTF_8).contains("\r\n"), "fixture lost its CRLF bytes")
        val doc = Corpus.load("crlf.md")
        assertFalse(doc.source.contains('\r'))
        assertTrue(doc.source.startsWith("# Fim de linha\n\nPrimeira linha\nsegunda"))
    }

    @Test
    fun `DOC-05 size policy thresholds`() {
        val mib = 1024L * 1024L
        assertEquals(SizeClass.NORMAL, SizePolicy.classify(0))
        assertEquals(SizeClass.NORMAL, SizePolicy.classify(8 * mib))
        assertEquals(SizeClass.SCROLL_ONLY, SizePolicy.classify(8 * mib + 1))
        assertEquals(SizeClass.SCROLL_ONLY, SizePolicy.classify(32 * mib))
        assertEquals(SizeClass.REFUSED, SizePolicy.classify(32 * mib + 1))
    }

    @Test
    fun `DOC-05 loader marks a 9 MB file scroll-only and still parses it`() {
        val bytes = ByteArray(9 * 1024 * 1024) { if (it % 80 == 79) '\n'.code.toByte() else 'a'.code.toByte() }
        val result = MarkdownLoader.load(bytes)
        result as LoadResult.Loaded
        assertEquals(SizeClass.SCROLL_ONLY, result.sizeClass)
        assertEquals(bytes.size, result.document.source.length)
    }

    @Test
    fun `DOC-05 loader refuses files above 32 MB without decoding`() {
        val refused = MarkdownLoader.load(ByteArray(0), declaredSize = 33L * 1024 * 1024)
        assertTrue(refused is LoadResult.Refused)
        assertEquals(33L * 1024 * 1024, (refused as LoadResult.Refused).sizeBytes)
    }
}
