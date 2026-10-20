package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.data.FolderGrant
import dev.dcesares.bifolio.data.OpenedFile
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.Paginator
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val IMAGE_PHONE = "w412dp-h915dp-xhdpi"
private const val IMAGE_TABLET = "w1280dp-h900dp-xhdpi"
private const val IMAGE_WAIT_MILLIS = 20_000L
private const val C16 = "images-relative/images"

private fun corpusImage(segments: List<String>): OpenedFile? {
    val file = File("../../testdata/corpus/images-relative", segments.joinToString("/"))
    return if (file.isFile) OpenedFile(file.inputStream(), null) else null
}

private fun sessionFor(
    document: Document,
    granted: Boolean,
): ImageSession {
    val session =
        ImageSession(
            document,
            if (granted) FolderGrant("tree", emptyList()) else null,
            ::corpusImage,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
    session.start()
    return session
}

/** What the view model does for a request: sizes from one snapshot reach the paginator and the detailer. */
private fun imageLayout(
    document: Document,
    request: PageRequest,
    session: ImageSession,
): PagedLayout {
    val snapshot = session.sizes.value
    val images = snapshot.scaledBy(request.density)
    val text = AndroidTextEngine().prepare(request)
    val geometry = GeometryCalculator.compute(text.spec)
    val flow = Paginator(document, text.measurer, geometry, images).paginateAll()!!
    return PagedLayout(
        request,
        geometry,
        text.config,
        flow,
        PageDetailer(document, text.measurer, geometry, images),
        snapshot.key,
    )
}

@Composable
private fun ImagePagedHarness(
    document: Document,
    session: ImageSession,
    spread: Int,
    banner: ImageBanner,
    onLayout: (PagedLayout) -> Unit,
    onDrawn: () -> Unit,
) {
    var layout by remember { mutableStateOf<PagedLayout?>(null) }
    var start by remember { mutableStateOf(Anchor(0, 0)) }
    var wanted by remember { mutableStateOf(if (spread == 0) Anchor(0, 0) else null) }
    Box(Modifier.fillMaxSize()) {
        key(start) {
            PagedSurface(
                layout = layout,
                initialAnchor = start,
                onRequest = { request ->
                    if (layout?.request != request) {
                        val built = imageLayout(document, request, session)
                        layout = built
                        onLayout(built)
                        if (spread > 0 && wanted == null) {
                            val at = (spread * built.flow.columnCount).coerceAtMost(built.flow.pages.lastIndex)
                            wanted = built.flow.pages[at].anchor
                            start = built.flow.pages[at].anchor
                        }
                    }
                },
                onSettled = {},
                onFirstDrawn = { if (start == wanted) onDrawn() },
                onToggleChrome = {},
                images = session.renderer,
                imageBanner = banner,
            )
        }
    }
}

/** SYN-08, PAG-07 (images in the page), UI-14 (the folder offer), corpus C-16. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val built = java.util.concurrent.CopyOnWriteArrayList<PagedLayout>()

    private fun paged(
        shotName: String,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        qualifiers: String = IMAGE_PHONE,
        fontScale: Float = 1f,
        spread: Int = 0,
        granted: Boolean = true,
        banner: ImageBanner = ImageBanner.Hidden,
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        val document = parsed(C16)
        val session = sessionFor(document, granted)
        val drawn = AtomicBoolean(false)
        built.clear()
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    ImagePagedHarness(document, session, spread, banner, { built.add(it) }) { drawn.set(true) }
                }
            }
        }
        // With a banner the page is first laid out for the whole area, then again for the area below the banner.
        val layouts = if (banner == ImageBanner.Hidden) 1 else 2
        compose.waitUntil(IMAGE_WAIT_MILLIS) {
            compose.onRoot().captureToImage()
            drawn.get() && built.size >= layouts
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$shotName.png")
    }

    private fun scroll(
        shotName: String,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        fontScale: Float = 1f,
        granted: Boolean = true,
        banner: ImageBanner = ImageBanner.Hidden,
    ) {
        RuntimeEnvironment.setQualifiers("+$IMAGE_PHONE")
        RuntimeEnvironment.setFontScale(fontScale)
        val document = parsed(C16)
        val session = sessionFor(document, granted)
        val ready = ReaderUiState.Ready(document, "Images", "c16", emptySet(), Anchor(0, 0))
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    ReaderScreen(ready, {}, images = session.renderer, imageBanner = banner)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$shotName.png")
    }

    @Test
    fun pagedGrantedPaper() = paged("images-paged-granted-paper-phone")

    @Test
    fun pagedGrantedSecondSpread() = paged("images-paged-granted-paper-phone-s1", spread = 1)

    @Test
    fun pagedGrantedNight() = paged("images-paged-granted-night-phone", ReadingThemeId.Night)

    @Test
    fun pagedGrantedFont200() = paged("images-paged-granted-paper-font200", fontScale = 2f)

    @Test
    fun pagedGrantedTablet() = paged("images-paged-granted-paper-tablet", qualifiers = IMAGE_TABLET)

    @Test
    fun pagedRejectedAndRemoteShowAltText() = paged("images-paged-placeholders-paper-phone", spread = 2)

    @Test
    fun pagedOfferBanner() =
        paged(
            "images-banner-offer-paper-phone",
            granted = false,
            banner = ImageBanner.Offer(null),
        )

    @Test
    fun theBannerTakesItsHeightFromThePageAndTheFirstLineStaysVisible() {
        paged("images-banner-reserves-height-paper-phone", granted = false, banner = ImageBanner.Offer(null))
        val whole = built.first()
        val below = built.last()
        assertTrue("the page area is shorter by the banner", below.request.heightPx < whole.request.heightPx)
        assertEquals(
            "both start at the same place",
            whole.flow.pages
                .first()
                .anchor,
            below.flow.pages
                .first()
                .anchor,
        )
        assertEquals(
            Anchor(0, 0),
            below.flow.pages
                .first()
                .anchor,
        )
    }

    @Test
    fun pagedOfferBannerNight() =
        paged(
            "images-banner-offer-night-phone",
            ReadingThemeId.Night,
            granted = false,
            banner = ImageBanner.Offer(null),
        )

    @Test
    fun pagedOfferBannerFont200() =
        paged(
            "images-banner-offer-paper-font200",
            fontScale = 2f,
            granted = false,
            banner = ImageBanner.Offer(null),
        )

    @Test
    fun pagedWrongFolderBanner() =
        paged(
            "images-banner-wrong-folder-paper-phone",
            granted = false,
            banner = ImageBanner.WrongFolder(null),
        )

    @Test
    fun scrollGrantedPaper() = scroll("images-scroll-granted-paper-phone")

    @Test
    fun scrollOfferBanner() =
        scroll("images-scroll-offer-paper-phone", granted = false, banner = ImageBanner.Offer(null))
}
