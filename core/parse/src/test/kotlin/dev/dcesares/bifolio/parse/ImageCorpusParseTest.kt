package dev.dcesares.bifolio.parse

import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.Paragraph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Corpus C-16 (`images-relative/`) and C-17 (`images-oversized.md`): SYN-08 classification on real files. */
class ImageCorpusParseTest {
    private val folder = "images-relative"

    private fun u16(
        b: ByteArray,
        at: Int,
    ) = (b[at].toInt() and 0xFF shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun u32(
        b: ByteArray,
        at: Int,
    ) = (u16(b, at) shl 16) or u16(b, at + 2)

    /** Width and height from a PNG IHDR. */
    private fun pngSize(b: ByteArray): Pair<Int, Int> {
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), (0..3).map { b[it].toInt() and 0xFF })
        return u32(b, 16) to u32(b, 20)
    }

    /** Width and height from the first SOF marker of a JPEG, and the EXIF orientation (1 when absent). */
    private fun jpegInfo(b: ByteArray): Triple<Int, Int, Int> {
        assertEquals(0xFFD8, u16(b, 0))
        var at = 2
        var orientation = 1
        while (at + 4 <= b.size) {
            val marker = u16(b, at)
            val length = u16(b, at + 2)
            if (marker == 0xFFE1 && String(b, at + 4, 4, Charsets.US_ASCII) == "Exif") {
                // TIFF header at at+10, big endian, one entry whose value sits 8 bytes into the entry.
                val tiff = at + 10
                assertEquals(0x4D4D, u16(b, tiff))
                val entry = tiff + u32(b, tiff + 4) + 2
                if (u16(b, entry) == 0x0112) orientation = u16(b, entry + 8)
            }
            if (marker in 0xFFC0..0xFFC2) return Triple(u16(b, at + 7), u16(b, at + 5), orientation)
            at += 2 + length
        }
        error("no SOF marker")
    }

    private fun blocks() = allBlocks(Corpus.load("$folder/images.md").blocks)

    @Test
    fun `C-16 the document has image blocks in order and one inline image kept in its paragraph`() {
        val images = blocks().filterIsInstance<ImageBlock>()
        assertEquals(
            listOf(
                "img/wide.png",
                "./img/tall.png",
                "img/with%20space.png",
                "img/rotated-exif.jpg",
                "img/plain.jpg",
                "img/missing.png",
                "../outside.png",
                "/etc/passwd",
                "https://example.com/picture.png",
                "file:///sdcard/picture.png",
            ),
            images.map { it.source },
        )
        assertEquals("A tall picture", images[1].title)
        val inline = blocks().filterIsInstance<Paragraph>().any { p -> p.content.any { it is Inline.Image } }
        assertTrue(inline)
    }

    @Test
    fun `C-16 every loadable reference resolves to a file that exists, nothing else does`() {
        val images = blocks().filterIsInstance<ImageBlock>()
        val sources = images.associate { it.source to ImageSources.classify(it.source) }
        val existing =
            listOf("img/wide.png", "./img/tall.png", "img/with%20space.png", "img/rotated-exif.jpg", "img/plain.jpg")
        for (s in existing) {
            val relative = sources.getValue(s) as ImageSource.Relative
            assertTrue(Corpus.bytes("$folder/${relative.path}").isNotEmpty(), s)
        }
        assertEquals(ImageSource.Relative(listOf("img", "with space.png")), sources["img/with%20space.png"])
        assertEquals(ImageSource.Relative(listOf("img", "tall.png")), sources["./img/tall.png"])
        assertEquals(ImageSource.Relative(listOf("img", "missing.png")), sources["img/missing.png"])
        assertEquals(ImageSource.Rejected(RejectReason.TRAVERSAL), sources["../outside.png"])
        assertEquals(ImageSource.Rejected(RejectReason.ABSOLUTE_PATH), sources["/etc/passwd"])
        assertEquals(ImageSource.Rejected(RejectReason.SCHEME), sources["file:///sdcard/picture.png"])
        assertTrue(sources["https://example.com/picture.png"] is ImageSource.Remote)
    }

    @Test
    fun `C-16 fixtures have the sizes the layout tests rely on, and the rotated JPEG says orientation 6`() {
        assertEquals(120 to 40, pngSize(Corpus.bytes("$folder/img/wide.png")))
        assertEquals(30 to 90, pngSize(Corpus.bytes("$folder/img/tall.png")))
        assertEquals(Triple(40, 30, 1), jpegInfo(Corpus.bytes("$folder/img/plain.jpg")))
        // Stored 48 x 16, shown 16 x 48: bounds must be swapped for orientations 5 to 8.
        assertEquals(Triple(48, 16, 6), jpegInfo(Corpus.bytes("$folder/img/rotated-exif.jpg")))
    }

    @Test
    fun `C-17 the oversized document embeds valid data URIs whose PNG sizes exceed any page`() {
        val images = allBlocks(Corpus.load("images-oversized.md").blocks).filterIsInstance<ImageBlock>()
        assertEquals(4, images.size)
        val sizes =
            images.map { block ->
                val data = ImageSources.classify(block.source) as ImageSource.DataUri
                assertEquals("image/png", data.mimeType)
                val bytes = data.decode()
                assertEquals(data.decodedSize, bytes.size)
                pngSize(bytes)
            }
        assertEquals(listOf(4000 to 300, 300 to 6000, 5000 to 5000, 32 to 32), sizes)
        assertTrue(images.all { it.source.length < ImageSources.MAX_DATA_URI_CHARS })
    }
}
