package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.data.FolderDocuments
import dev.dcesares.bifolio.data.FolderGrantRepository
import dev.dcesares.bifolio.data.OpenedFile
import dev.dcesares.bifolio.data.ReadingPreferences
import dev.dcesares.bifolio.data.RecentsRepository
import dev.dcesares.bifolio.layout.ImageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream

private const val AUTH = "com.android.externalstorage.documents"
private const val DOC = "content://$AUTH/document/primary%3ADocs%2Fnotes%2Fa.md"
private const val TREE = "content://$AUTH/tree/primary%3ADocs"
private const val OTHER_TREE = "content://$AUTH/tree/primary%3AElsewhere"

private const val SIBLING = "content://$AUTH/document/primary%3ADocs%2Fnotes%2Fb.md"
private const val ELSEWHERE = "content://$AUTH/document/primary%3ADocs%2Fother%2Fc.md"

/** A provider that answers "where is this document in the tree" only when released, like a slow IPC. */
private class SlowProvider(
    private val gate: java.util.concurrent.CountDownLatch,
) : FolderDocuments {
    override fun namesFromRoot(
        treeUri: String,
        documentUri: String,
    ): List<String>? {
        gate.await()
        return listOf("notes", "a.md")
    }

    override fun open(
        treeUri: String,
        names: List<String>,
    ): OpenedFile? = null
}

private val PHONE =
    PageRequest(widthPx = 400, heightPx = 640, density = 1f, fontScale = 1f, bodySp = 18f, languageTag = "en")

private class OneFile(
    private val bytes: ByteArray,
) : FolderDocuments {
    override fun namesFromRoot(
        treeUri: String,
        documentUri: String,
    ): List<String>? = null

    override fun open(
        treeUri: String,
        names: List<String>,
    ): OpenedFile? =
        if (names ==
            listOf("notes", "pic.png")
        ) {
            OpenedFile(ByteArrayInputStream(bytes), "image/png")
        } else {
            null
        }
}

/** UI-14 (offer, wrong folder, dismiss once), PAG-07 (a new size snapshot repaginates from the anchor), SYN-08. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImageViewModelTest {
    private val prefs = InMemoryPreferences()
    private val grants = RecordingGrants()
    private val folders = FolderGrantRepository(prefs, grants, OneFile(pngBytes(40, 20)))
    private val text = "Before.\n\n![pic](pic.png)\n\nAfter.\n"

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(
        uri: String = DOC,
        repository: FolderGrantRepository = folders,
    ): ReaderViewModel {
        val vm =
            ReaderViewModel(
                reader = FakeReader(mapOf(DOC to raw(text), SIBLING to raw(text), ELSEWHERE to raw(text))),
                repository = RecentsRepository(InMemoryPreferences(), RecordingGrants()),
                preferences = ReadingPreferences(InMemoryPreferences()),
                engine = GridTextEngine(),
                computeDispatcher = Dispatchers.Main,
                folders = repository,
                imageIo = Dispatchers.Main,
            )
        vm.open(OpenRequest(uri, "text/markdown", persistGrant = false))
        return vm
    }

    @Test
    fun aDocumentWithRelativeImagesOffersTheFolderAtTheDocumentsFolder() {
        val banner = viewModel().imageBanner.value
        assertTrue("$banner", banner is ImageBanner.Offer)
        assertEquals("content://$AUTH/document/primary%3ADocs%2Fnotes", (banner as ImageBanner.Offer).pickerStart)
    }

    @Test
    fun dismissingIsRememberedForThatDocument() {
        val vm = viewModel()
        vm.dismissImageBanner()
        assertEquals(ImageBanner.Hidden, vm.imageBanner.value)
        assertEquals(ImageBanner.Hidden, viewModel().imageBanner.value)
    }

    @Test
    fun aFolderThatDoesNotHoldTheDocumentIsRefusedAndAsksAgain() {
        val vm = viewModel()
        vm.onFolderPicked(OTHER_TREE)
        // Asking the provider hops to the IO dispatcher, so the answer is not synchronous.
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.imageBanner.value is ImageBanner.Offer && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("${vm.imageBanner.value}", vm.imageBanner.value is ImageBanner.WrongFolder)
        assertTrue("no permission was taken", grants.held.isEmpty())
    }

    @Test
    fun dismissingIsRememberedForTheWholeFolder() {
        viewModel().dismissImageBanner()
        assertEquals("a sibling in the same folder", ImageBanner.Hidden, viewModel(SIBLING).imageBanner.value)
        val other = viewModel(ELSEWHERE).imageBanner.value
        assertTrue("another folder is still offered: $other", other is ImageBanner.Offer)
    }

    @Test
    fun aFolderAnsweredAfterTheDocumentWasClosedIsNotAppliedToIt() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val slow = FolderGrantRepository(prefs, grants, SlowProvider(gate))
        val vm = viewModel(repository = slow)
        // Ids that do not reveal the path make the repository ask the provider, which is slow.
        vm.onFolderPicked("content://other.provider/tree/root")
        vm.close()
        gate.countDown()
        val deadline = System.currentTimeMillis() + 5_000
        while (grants.held.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(200)
        assertEquals(null, vm.imageSession.value)
        assertEquals(ImageBanner.Hidden, vm.imageBanner.value)
    }

    @Test
    fun theRightFolderHidesTheBannerAndIsFoundAgainOnTheNextOpen() {
        val vm = viewModel()
        vm.onFolderPicked(TREE)
        assertEquals(ImageBanner.Hidden, vm.imageBanner.value)
        assertEquals(setOf(TREE), grants.held)
        assertEquals("the grant is reused, nothing is asked", ImageBanner.Hidden, viewModel().imageBanner.value)
    }

    @Test
    fun aNewSizeSnapshotRepaginatesFromTheAnchorWithANewKey() {
        val vm = viewModel()
        vm.paginate(PHONE)
        assertEquals(0, vm.paged.value?.imageKey)
        vm.onAnchorSettled(
            dev.dcesares.bifolio.model
                .Anchor(2, 0),
        )
        vm.onFolderPicked(TREE)
        val session = vm.imageSession.value!!
        val snapshot = session.sizes.value
        assertTrue(snapshot.key != 0)
        assertEquals(ImageSize.Known(40, 20), snapshot.sizeOf(session.blocks.single()))
        val layout = vm.paged.value
        assertNotNull(layout)
        assertEquals("layout made from the current snapshot", snapshot.key, layout!!.imageKey)
        assertEquals(
            "the anchor does not move",
            dev.dcesares.bifolio.model
                .Anchor(2, 0),
            vm.anchor.value,
        )
        val spread = layout.flow.spreadIndexOf(vm.anchor.value)
        assertTrue(spread >= 0)
    }

    @Test
    fun closingStopsTheImages() {
        val vm = viewModel()
        vm.close()
        assertEquals(null, vm.imageSession.value)
        assertEquals(ImageBanner.Hidden, vm.imageBanner.value)
    }
}
