package dev.dcesares.bifolio.feature.reader

import android.graphics.Bitmap
import dev.dcesares.bifolio.data.FolderGrant
import dev.dcesares.bifolio.data.OpenedFile
import dev.dcesares.bifolio.layout.ImageSize
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.SourceRange
import dev.dcesares.bifolio.parse.MarkdownParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

private val IMAGES = File("../../testdata/corpus/images-relative")

internal fun pngBytes(
    width: Int,
    height: Int,
): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** Files of the C-16 corpus, opened the way a granted folder would: segments from the folder root. */
private fun corpusFile(segments: List<String>): OpenedFile? {
    val file = File(IMAGES, segments.joinToString("/"))
    return if (file.isFile) OpenedFile(file.inputStream(), null) else null
}

private fun block(
    source: String,
    start: Int,
    alt: String = "alt",
) = ImageBlock(source, null, alt, SourceRange(start, start + 10))

/** SYN-08: formats are told by their bytes, SVG never loads; PAG-07: sizes come from a bounds-only read. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RasterImagesTest {
    private fun probe(path: String) = RasterImages.probe { corpusFile(path.split("/")) }

    @Test
    fun theSizeOfAPngComesFromItsHeader() {
        val probe = probe("img/wide.png")
        assertNotNull(probe)
        assertTrue(probe!!.width > probe.height)
    }

    @Test
    fun orientationSixSwapsWidthAndHeight() {
        // Stored 48 x 16, EXIF orientation 6: shown upright it is 16 x 48.
        val probe = probe("img/rotated-exif.jpg")!!
        assertEquals(6, probe.orientation)
        assertEquals(16, probe.width)
        assertEquals(48, probe.height)
    }

    @Test
    fun theDecodedBitmapIsTurnedUprightToo() {
        val bitmap = RasterImages.decode({ corpusFile(listOf("img", "rotated-exif.jpg")) }, 16, 48)!!
        assertEquals(16, bitmap.width)
        assertEquals(48, bitmap.height)
    }

    @Test
    fun aMissingFileGivesNothing() {
        assertNull(probe("img/missing.png"))
        assertNull(RasterImages.decode({ null }, 10, 10))
    }

    @Test
    fun bytesThatAreNotAnImageGiveNothing() {
        val text = "not an image at all, just text".toByteArray()
        assertNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(text), "image/png") })
    }

    @Test
    fun svgIsRejectedWhateverItDeclares() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"4\"/>".toByteArray()
        assertNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(svg), "image/svg+xml") })
        assertNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(svg), "image/png") })
        assertNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(svg), null) })
    }

    @Test
    fun theDeclaredTypeMustMatchTheBytes() {
        val png = pngBytes(4, 3)
        assertNotNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(png), "image/png") })
        assertNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(png), "image/jpeg") })
        assertNotNull(RasterImages.probe { OpenedFile(ByteArrayInputStream(png), null) })
    }

    @Test
    fun aLargeImageIsSubsampledNotLoadedWhole() {
        val png = pngBytes(256, 128)
        val bitmap = RasterImages.decode({ OpenedFile(ByteArrayInputStream(png), "image/png") }, 64, 32)!!
        assertTrue("decoded ${bitmap.width}", bitmap.width in 64..128)
    }

    @Test
    fun theSniffer() {
        assertEquals(ImageFormat.PNG, ImageSniffer.sniff(pngBytes(1, 1)))
        assertNull(ImageSniffer.sniff("<?xml version=\"1.0\"?><svg/>".toByteArray()))
        assertNull(ImageSniffer.sniff(ByteArray(0)))
    }
}

/** PAG-07: a size once reported is never taken back; every new snapshot has a key of its own. */
class ImageSizeSnapshotTest {
    @Test
    fun emptyHasKeyZeroAndKnowsNothing() {
        assertEquals(0, ImageSizeSnapshot.EMPTY.key)
        assertEquals(ImageSize.Unknown, ImageSizeSnapshot.EMPTY.sizeOf(block("a.png", 3)))
    }

    @Test
    fun aNewSizeMakesANewKeyAndKeepsTheOldOnes() {
        val first = ImageSizeSnapshot.EMPTY.plus(mapOf(3 to ImageSize.Known(10, 20)))
        val second = first.plus(mapOf(40 to ImageSize.Known(5, 5)))
        assertTrue(first.key != 0)
        assertTrue(second.key != first.key)
        assertEquals(ImageSize.Known(10, 20), second.sizeOf(block("a.png", 3)))
        assertEquals(ImageSize.Known(5, 5), second.sizeOf(block("b.png", 40)))
        assertEquals(ImageSize.Unknown, first.sizeOf(block("b.png", 40)))
    }

    @Test
    fun nothingNewKeepsTheSameSnapshot() {
        val first = ImageSizeSnapshot.EMPTY.plus(mapOf(3 to ImageSize.Known(10, 20)))
        assertSame(first, first.plus(mapOf(3 to ImageSize.Known(99, 99))))
        assertEquals(ImageSize.Known(10, 20), first.plus(mapOf(3 to ImageSize.Known(99, 99))).sizeOf(block("a", 3)))
    }

    @Test
    fun densityScalesPixelsToLayoutPixels() {
        val scaled = ImageSizeSnapshot.EMPTY.plus(mapOf(3 to ImageSize.Known(10, 20))).scaledBy(2.5f)
        assertEquals(ImageSize.Known(25, 50), scaled.sizeOf(block("a.png", 3)))
        assertEquals(ImageSize.Unknown, scaled.sizeOf(block("b.png", 9)))
    }
}

/** SYN-08, UI-14: which images a folder is needed for, how sizes and pixels arrive, what failure does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImageSessionTest {
    private val text = File(IMAGES, "images.md").readText().replace("\r\n", "\n")
    private val document = MarkdownParser.parse(text)

    private fun session(
        folder: FolderGrant? = FolderGrant("tree", emptyList()),
        cacheBytes: Int = 1 shl 20,
        doc: dev.dcesares.bifolio.model.Document = document,
    ) = ImageSession(
        doc,
        folder,
        ::corpusFile,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        Dispatchers.Unconfined,
        cacheBytes,
    )

    private fun blockFor(
        session: ImageSession,
        source: String,
    ) = session.blocks.first { it.source == source }

    @Test
    fun relativeImagesNeedAFolderUntilOneIsGranted() {
        assertTrue(session(folder = null).needsFolder())
        assertFalse(session().needsFolder())
    }

    @Test
    fun aDocumentWithoutRelativeImagesNeverAsks() {
        val remote = MarkdownParser.parse("![r](https://example.com/a.png)\n\n![a](/etc/passwd)\n")
        assertFalse(session(folder = null, doc = remote).needsFolder())
    }

    @Test
    fun aReferenceAboveTheDocumentFolderAsksToo() {
        val up = MarkdownParser.parse("![u](../pic.png)\n")
        assertTrue(session(folder = null, doc = up).needsFolder())
    }

    @Test
    fun sizesAreReadWithoutDecodingAndPublishedOnce() {
        val session = session()
        assertEquals(0, session.sizes.value.key)
        session.start()
        val snapshot = session.sizes.value
        assertTrue(snapshot.key != 0)
        assertTrue(snapshot.sizeOf(blockFor(session, "img/wide.png")) is ImageSize.Known)
        assertEquals(
            ImageSize.Known(16, 48),
            snapshot.sizeOf(blockFor(session, "img/rotated-exif.jpg")),
        )
        assertEquals(ImageSize.Unknown, snapshot.sizeOf(blockFor(session, "img/missing.png")))
        assertEquals(ImageSize.Unknown, snapshot.sizeOf(blockFor(session, "https://example.com/picture.png")))
    }

    @Test
    fun withoutAFolderNothingLoadsAndEveryBoxIsAPlaceholder() {
        val session = session(folder = null)
        session.start()
        assertEquals(0, session.sizes.value.key)
        val wide = blockFor(session, "img/wide.png")
        assertNull(session.renderer.bitmapFor(wide, 40, 20))
        assertTrue(session.renderer.isPlaceholder(wide, 40, 20))
        assertTrue(session.renderer.showsAlt(wide, 40, 20))
    }

    @Test
    fun pixelsArriveAtTheBoxSizeAndAreCached() {
        val session = session()
        session.start()
        val wide = blockFor(session, "img/wide.png")
        val size = session.sizes.value.sizeOf(wide) as ImageSize.Known
        val before = session.renderer.observe()
        assertNull(session.renderer.bitmapFor(wide, size.width, size.height))
        val again = session.renderer.bitmapFor(wide, size.width, size.height)
        assertNotNull(again)
        assertTrue(session.renderer.observe() > before)
        assertFalse(session.renderer.isPlaceholder(wide, size.width, size.height))
    }

    @Test
    fun aBoxOfZeroSizeStillDecodes() {
        val session = session()
        session.start()
        val wide = blockFor(session, "img/wide.png")
        assertNull(session.renderer.bitmapFor(wide, 0, 0))
        assertNotNull(session.renderer.bitmapFor(wide, 0, 0))
    }

    @Test
    fun aFailedDecodeLeavesTheReportedSizeAlone() {
        var broken = false
        val document = MarkdownParser.parse("![w](img/wide.png)\n")
        val session =
            ImageSession(
                document,
                FolderGrant("tree", emptyList()),
                { segments ->
                    if (broken) {
                        OpenedFile(
                            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
                            "image/png",
                        )
                    } else {
                        corpusFile(segments)
                    }
                },
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
                1 shl 20,
            )
        session.start()
        val wide = session.blocks.single()
        val known = session.sizes.value
        assertTrue(known.sizeOf(wide) is ImageSize.Known)
        broken = true
        assertNull(session.renderer.bitmapFor(wide, 8, 8))
        assertTrue(session.renderer.isPlaceholder(wide, 8, 8))
        assertFalse("another box size is tried again", session.renderer.isPlaceholder(wide, 9, 9))
        assertSame("a failed decode must not publish a new snapshot", known, session.sizes.value)
        assertEquals(known.sizeOf(wide), session.sizes.value.sizeOf(wide))
    }

    @Test
    fun aBitmapBiggerThanTheCacheIsNotDecodedAgainAtEveryDraw() {
        val session = session(cacheBytes = 64)
        session.start()
        val wide = blockFor(session, "img/wide.png")
        val size = session.sizes.value.sizeOf(wide) as ImageSize.Known
        assertNull(session.renderer.bitmapFor(wide, size.width, size.height))
        val revision = session.renderer.observe()
        assertNull(session.renderer.bitmapFor(wide, size.width, size.height))
        assertEquals("no second decode was started", revision, session.renderer.observe())
        assertTrue(session.renderer.isPlaceholder(wide, size.width, size.height))
    }

    @Test
    fun dataUrisDecodeOnlyAfterTheLengthCheck() {
        val encoded =
            java.util.Base64
                .getEncoder()
                .encodeToString(pngBytes(6, 4))
        val md = MarkdownParser.parse("![d](data:image/png;base64,$encoded)\n")
        val session = session(folder = null, doc = md)
        session.start()
        assertEquals(ImageSize.Known(6, 4), session.sizes.value.sizeOf(session.blocks.single()))
        assertFalse(session.needsFolder())
        val huge = MarkdownParser.parse("![d](data:image/png;base64,${"A".repeat(2_000_000)})\n")
        val refused = session(folder = null, doc = huge)
        refused.start()
        assertEquals(0, refused.sizes.value.key)
    }

    @Test
    fun imagesInsideContainersAreFound() {
        val nested = MarkdownParser.parse("- item\n\n  ![a](a.png)\n\n> ![b](b.png)\n")
        assertEquals(listOf("a.png", "b.png"), imageBlocksOf(nested.blocks).map { it.source })
        assertNotSame(ImageSizeSnapshot.EMPTY, ImageSizeSnapshot.EMPTY.plus(mapOf(1 to ImageSize.Known(1, 1))))
    }
}
