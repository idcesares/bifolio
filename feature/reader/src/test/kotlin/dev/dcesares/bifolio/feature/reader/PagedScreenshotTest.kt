package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.layout.FoldOcclusion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.Paginator
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val PHONE = "w412dp-h915dp-xhdpi"
private const val COVER = "w360dp-h800dp-xhdpi"
private const val TABLET = "w1280dp-h900dp-xhdpi"
private const val FOLDABLE = "w840dp-h900dp-xhdpi"
private const val FOLD_CENTER = 840 // px at xhdpi: the middle of the 840dp window
private const val HINGE_HALF = 24 // 24dp wide hinge, in px
private const val WAIT_MILLIS = 20_000L

internal fun parsed(name: String): Document =
    MarkdownParser.parse(File("../../testdata/corpus/$name.md").readText().replace("\r\n", "\n"))

/** Paginates synchronously with the real `StaticLayout` engine, the way the view model does in the app. */
internal fun buildLayout(
    document: Document,
    request: PageRequest,
): PagedLayout {
    val text = AndroidTextEngine().prepare(request)
    val geometry = GeometryCalculator.compute(text.spec)
    val flow = Paginator(document, text.measurer, geometry).paginateAll()!!
    return PagedLayout(request, geometry, text.config, flow, PageDetailer(document, text.measurer, geometry))
}

/**
 * Shows [document] on the paged surface at spread [spread]. The first composition paginates for the window;
 * the harness then restarts the surface on that spread's first anchor, exactly as a resumed reader would.
 */
@Composable
private fun PagedHarness(
    document: Document,
    spread: Int,
    fold: FoldSpec?,
    chrome: Boolean,
    onDrawn: () -> Unit,
) {
    var layout by remember { mutableStateOf<PagedLayout?>(null) }
    var start by remember { mutableStateOf(Anchor(0, 0)) }
    var wanted by remember { mutableStateOf(if (spread == 0) Anchor(0, 0) else null) }
    val foldSource = remember(fold) { FakeFoldSource(fold) }
    Box(Modifier.fillMaxSize()) {
        key(start) {
            PagedSurface(
                layout = layout,
                initialAnchor = start,
                onRequest = { request ->
                    if (layout?.request != request) {
                        val built = buildLayout(document, request)
                        layout = built
                        if (spread > 0 && wanted == null) {
                            val at = (spread * built.flow.columnCount).coerceAtMost(built.flow.pages.lastIndex)
                            val first = built.flow.pages[at]
                            wanted = first.anchor
                            start = first.anchor
                        }
                    }
                },
                onSettled = {},
                onFirstDrawn = { if (start == wanted) onDrawn() },
                onToggleChrome = {},
                foldSource = foldSource,
            )
        }
        if (fold != null) FoldOverlay(fold)
        ReaderChrome(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Test-only drawing of the hinge: translucent band for its bounds, hairline when it has no width. */
@Composable
internal fun FoldOverlay(fold: FoldSpec) {
    Canvas(Modifier.fillMaxSize()) {
        val width = (fold.right - fold.left).coerceAtLeast(2).toFloat()
        val height = (fold.bottom - fold.top).coerceAtLeast(2).toFloat()
        drawRect(
            color = Color.Red.copy(alpha = 0.25f),
            topLeft = Offset(fold.left.toFloat(), fold.top.toFloat()),
            size = Size(width, height),
        )
    }
}

/** Paged reading surface, corpus C-01 to C-13 and C-22 on phone, cover and tablet (SCO-03, PAG-03..08, LAY-04..09). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PagedScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun paged(
        file: String,
        shotName: String,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        qualifiers: String = PHONE,
        fontScale: Float = 1f,
        spread: Int = 0,
        fold: FoldSpec? = null,
        chrome: Boolean = false,
    ) {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        val document = parsed(file)
        val drawn = AtomicBoolean(false)
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) { PagedHarness(document, spread, fold, chrome) { drawn.set(true) } }
            }
        }
        compose.waitUntil(WAIT_MILLIS) {
            // Robolectric only draws when asked; a capture forces a pass, which is what sets `drawn`.
            compose.onRoot().captureToImage()
            drawn.get()
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$shotName.png")
    }

    @Test
    fun c01PhonePaper() = paged("prose-short", "paged-c01-prose-paper-phone")

    /** UI-03, UI-04: the chrome over the page after a center tap. The page underneath does not move. */
    @Test
    fun chromeVisiblePhonePaper() = paged("prose-short", "paged-chrome-visible-paper-phone", chrome = true)

    @Test
    fun c01CoverNight() = paged("prose-short", "paged-c01-prose-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun c01TabletSepia() = paged("prose-short", "paged-c01-prose-sepia-tablet", ReadingThemeId.Sepia, TABLET)

    @Test
    fun c03PhonePaper() = paged("headings-all-levels", "paged-c03-headings-paper-phone")

    @Test
    fun c03PhoneFont200() = paged("headings-all-levels", "paged-c03-headings-paper-font200", fontScale = 2f)

    @Test
    fun c04PhonePaper() = paged("headings-non-latin", "paged-c04-headings-nonlatin-paper-phone")

    @Test
    fun c05PhonePaper() = paged("lists-nested", "paged-c05-lists-paper-phone")

    @Test
    fun c05CoverNight() = paged("lists-nested", "paged-c05-lists-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun c05TabletPaper() = paged("lists-nested", "paged-c05-lists-paper-tablet", qualifiers = TABLET)

    @Test
    fun c06PhonePaper() = paged("tasks", "paged-c06-tasks-paper-phone")

    @Test
    fun c07PhonePaper() = paged("quotes-and-callouts", "paged-c07-quotes-paper-phone")

    @Test
    fun c07CoverNight() = paged("quotes-and-callouts", "paged-c07-quotes-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun c07TabletPaper() = paged("quotes-and-callouts", "paged-c07-quotes-paper-tablet", qualifiers = TABLET)

    @Test
    fun c08PhoneNight() = paged("code-languages", "paged-c08-code-night-phone", ReadingThemeId.Night)

    @Test
    fun c09PhoneFirstPage() = paged("code-long-block", "paged-c09-codelong-paper-phone-p1")

    @Test
    fun c09PhoneContinuation() = paged("code-long-block", "paged-c09-codelong-paper-phone-p2", spread = 1)

    @Test
    fun c09TabletSpread() =
        paged("code-long-block", "paged-c09-codelong-paper-tablet-p2", qualifiers = TABLET, spread = 1)

    @Test
    fun c10PhonePaper() = paged("code-long-lines", "paged-c10-codelines-paper-phone")

    @Test
    fun c11PhonePaper() = paged("tables-simple", "paged-c11-tables-paper-phone")

    @Test
    fun c11CoverNight() = paged("tables-simple", "paged-c11-tables-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun c11PhoneFont200() = paged("tables-simple", "paged-c11-tables-paper-font200", fontScale = 2f)

    @Test
    fun c12PhoneFirstPage() = paged("tables-tall", "paged-c12-tablestall-paper-phone-p1")

    @Test
    fun c12PhoneRepeatedHeader() = paged("tables-tall", "paged-c12-tablestall-paper-phone-p2", spread = 1)

    @Test
    fun c12TabletSpread() = paged("tables-tall", "paged-c12-tablestall-paper-tablet", qualifiers = TABLET)

    @Test
    fun c13PhonePaper() = paged("tables-wide", "paged-c13-tableswide-paper-phone")

    @Test
    fun c13CoverNight() = paged("tables-wide", "paged-c13-tableswide-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun c13TabletPaper() = paged("tables-wide", "paged-c13-tableswide-paper-tablet", qualifiers = TABLET)

    @Test
    fun c22PhoneFirstPage() = paged("widows-orphans", "paged-c22-widows-paper-phone-p1")

    @Test
    fun c22PhoneSecondPage() = paged("widows-orphans", "paged-c22-widows-paper-phone-p2", spread = 1)

    @Test
    fun c22CoverSecondPage() =
        paged("widows-orphans", "paged-c22-widows-night-cover-p2", ReadingThemeId.Night, COVER, spread = 1)

    @Test
    fun c22TabletSpread() = paged("widows-orphans", "paged-c22-widows-paper-tablet", qualifiers = TABLET)

    /** SYN-13, PAG-07: the metadata card above the first heading, title and flat rows. */
    @Test
    fun frontMatterPhonePaper() = paged("front-matter", "paged-front-matter-paper-phone")

    @Test
    fun frontMatterCoverNight() = paged("front-matter", "paged-front-matter-night-cover", ReadingThemeId.Night, COVER)

    @Test
    fun frontMatterPhoneFont200() = paged("front-matter", "paged-front-matter-paper-font200", fontScale = 2f)

    private fun verticalFold(
        halfOpened: Boolean,
        occluding: Boolean,
    ) = FoldSpec(
        left = if (occluding) FOLD_CENTER - HINGE_HALF else FOLD_CENTER,
        top = 0,
        right = if (occluding) FOLD_CENTER + HINGE_HALF else FOLD_CENTER,
        bottom = 1800,
        isSeparating = true,
        orientation = FoldOrientation.Vertical,
        halfOpened = halfOpened,
        occlusion = if (occluding) FoldOcclusion.Full else FoldOcclusion.None,
    )

    /** DM-06: flat, hinge hides pixels, gutter covers the hinge plus margins (LAY-10, LAY-12, POS-03). */
    @Test
    fun c22FoldableFlatSeparatingSpread() =
        paged(
            "widows-orphans",
            "paged-c22-widows-paper-foldable-flat-separating",
            qualifiers = FOLDABLE,
            fold = verticalFold(halfOpened = false, occluding = true),
        )

    /** DM-05: half opened, book posture, gutter on the fold with the extra inner margin (LAY-13, POS-03). */
    @Test
    fun c22FoldableBookSpread() =
        paged(
            "widows-orphans",
            "paged-c22-widows-paper-foldable-book",
            qualifiers = FOLDABLE,
            fold = verticalFold(halfOpened = true, occluding = false),
        )
}
