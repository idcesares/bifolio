package dev.dcesares.bifolio.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.layout.FoldOcclusion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import dev.dcesares.bifolio.layout.Posture
import dev.dcesares.bifolio.layout.PxRect
import dev.dcesares.bifolio.model.Anchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val DM_WAIT_MILLIS = 20_000L
private const val DM_HINGE_DP = 24
private const val SCRUBBER_BAND_DP = 9

private const val Q_PHONE = "w412dp-h915dp-xhdpi"
private const val Q_PHONE_LAND = "w915dp-h412dp-xhdpi"
private const val Q_COVER = "w360dp-h800dp-xhdpi"

/** Real inner screen 2256 x 2504 px at 480 dpi: 752 x 835 dp is 2256 x 2505 px, the closest whole-dp size. */
private const val Q_INNER = "w752dp-h835dp-xxhdpi"
private const val Q_WIDE = "w960dp-h720dp-xhdpi"
private const val Q_TABLET = "w1280dp-h900dp-xhdpi"
private const val Q_NARROW = "w320dp-h640dp-xhdpi"
private const val Q_SPLIT = "w420dp-h900dp-xhdpi"

/** Window size in px, read from the running configuration. */
private class Win(
    val width: Int,
    val height: Int,
    val density: Float,
)

private fun verticalFold(
    win: Win,
    halfOpened: Boolean,
    occluding: Boolean,
    separating: Boolean,
): FoldSpec {
    val center = win.width / 2
    val half = if (occluding) (DM_HINGE_DP * win.density / 2f).toInt() else 0
    return FoldSpec(
        left = center - half,
        top = 0,
        right = center + half,
        bottom = win.height,
        isSeparating = separating,
        orientation = FoldOrientation.Vertical,
        halfOpened = halfOpened,
        occlusion = if (occluding) FoldOcclusion.Full else FoldOcclusion.None,
    )
}

@Composable
private fun MatrixHarness(
    document: dev.dcesares.bifolio.model.Document,
    fold: FoldSpec?,
    chrome: Boolean,
    onBuilt: (PagedLayout) -> Unit,
    onDrawn: () -> Unit,
    overlay: () -> Boolean,
) {
    var layout by remember { mutableStateOf<PagedLayout?>(null) }
    val foldSource = remember(fold) { FakeFoldSource(fold) }
    Box(Modifier.fillMaxSize()) {
        PagedSurface(
            layout = layout,
            initialAnchor = Anchor(0, 0),
            onRequest = { request ->
                if (layout?.request != request) {
                    val built = buildLayout(document, request)
                    onBuilt(built)
                    layout = built
                }
            },
            onSettled = {},
            onFirstDrawn = onDrawn,
            onToggleChrome = {},
            foldSource = foldSource,
            document = document,
        )
        if (fold != null && overlay()) FoldOverlay(fold)
        ReaderChrome(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** What a rendered case reports back to its test. */
private class Rendered(
    val layout: PagedLayout,
    val win: Win,
    val bitmap: Bitmap,
)

/**
 * Device matrix DM-01 to DM-12 (docs/TESTING.md section 2) on the paged surface, Paper and Night. The fold is a
 * test-only overlay drawn after the page is captured clean; LAY-14 is asserted on the clean bitmap.
 * RTL is not covered: [PagedSurface] does not mirror columns, tap zones or the turn direction yet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DeviceMatrixScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(
        file: String,
        qualifiers: String,
        theme: ReadingThemeId,
        fontScale: Float = 1f,
        fold: ((Win) -> FoldSpec?)? = null,
        chrome: Boolean = false,
        shot: String? = null,
    ): Rendered {
        RuntimeEnvironment.setQualifiers("+$qualifiers")
        RuntimeEnvironment.setFontScale(fontScale)
        val metrics = RuntimeEnvironment.getApplication().resources.displayMetrics
        val win = Win(metrics.widthPixels, metrics.heightPixels, metrics.density)
        val foldSpec = fold?.invoke(win)
        val document = parsed(file)
        val drawn = AtomicBoolean(false)
        val built = AtomicReference<PagedLayout?>(null)
        var overlayOn by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    MatrixHarness(document, foldSpec, chrome, { built.set(it) }, { drawn.set(true) }) { overlayOn }
                }
            }
        }
        compose.waitUntil(DM_WAIT_MILLIS) {
            compose.onRoot().captureToImage()
            drawn.get()
        }
        compose.waitForIdle()
        val bitmap =
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .copy(Bitmap.Config.ARGB_8888, false)
        assertEquals("window width px", win.width, bitmap.width)
        assertEquals("window height px", win.height, bitmap.height)
        if (shot != null) {
            overlayOn = true
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/screenshots/$shot.png")
        }
        return Rendered(built.get()!!, win, bitmap)
    }

    /** Number of pixels in the columns [from] until [to] that differ from the page background (corner pixel). */
    private fun ink(
        bitmap: Bitmap,
        from: Int,
        to: Int,
    ): Int {
        val bg = bitmap.getPixel(0, 0)
        val a = from.coerceIn(0, bitmap.width)
        val b = to.coerceIn(0, bitmap.width)
        if (b <= a) return 0
        val row = IntArray(b - a)
        var count = 0
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, row.size, a, y, row.size, 1)
            count += row.count { it != bg }
        }
        return count
    }

    /** Bounding box x0,y0..x1,y1 of the non-background pixels in columns [from] until [to], for failure messages. */
    private fun inkBox(
        bitmap: Bitmap,
        from: Int,
        to: Int,
    ): String {
        val bg = bitmap.getPixel(0, 0)
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = -1
        var y1 = -1
        for (y in 0 until bitmap.height) {
            for (x in from.coerceAtLeast(0) until to.coerceAtMost(bitmap.width)) {
                if (bitmap.getPixel(x, y) == bg) continue
                x0 = minOf(x0, x)
                x1 = maxOf(x1, x)
                y0 = minOf(y0, y)
                y1 = maxOf(y1, y)
            }
        }
        return "$x0,$y0..$x1,$y1"
    }

    /** LAY-14: no ink inside fold bounds plus innerMargin; both sides show ink when there are two columns. */
    private fun assertFoldBandClear(
        r: Rendered,
        bothSides: Boolean = true,
    ) {
        val g = r.layout.geometry
        val fold = checkNotNull(g.fold) { "geometry has no fold" }
        val left = fold.left - g.innerMargin
        val right = fold.right + g.innerMargin
        val inBand = ink(r.bitmap, left, right)
        val report =
            "charW=${r.layout.config.charWidthPx} outer=${g.outerMargin} inner=${g.innerMargin} gutter=${g.gutter} " +
                "lefts=${g.columnLefts} widths=${g.columnWidths} band=$left..$right fold=${fold.left}..${fold.right}"
        assertEquals("ink inside fold band: $report box=${inkBox(r.bitmap, left, right)}", 0, inBand)
        val sides = ink(r.bitmap, 0, left) + ink(r.bitmap, right, r.bitmap.width)
        assertTrue("no ink at all, vacuous check: $report", sides > 0)
        if (g.columnCount == 2 && bothSides) {
            assertTrue("no ink left of the fold: $report", ink(r.bitmap, 0, left) > 0)
            assertTrue("no ink right of the fold: $report", ink(r.bitmap, right, r.bitmap.width) > 0)
        }
        println("DM-REPORT $report")
    }

    /** Nothing clips: the outer margins stay empty. */
    private fun assertOuterMarginsClear(r: Rendered) {
        val g = r.layout.geometry
        val lo = g.columnLefts.first()
        val hi = g.columnLefts.last() + g.columnWidths.last()
        assertEquals("ink left of the first column", 0, ink(r.bitmap, 0, lo))
        assertEquals("ink right of the last column", 0, ink(r.bitmap, hi, r.bitmap.width))
    }

    private fun shotName(
        id: String,
        slug: String,
        theme: ReadingThemeId,
    ) = "dm-$id-$slug-${theme.name.lowercase()}"

    private fun plain(
        id: String,
        slug: String,
        q: String,
        theme: ReadingThemeId,
        columns: Int? = null,
        chrome: Boolean = false,
    ) {
        val r = render("widows-orphans", q, theme, chrome = chrome, shot = shotName(id, slug, theme))
        assertNull("no fold expected", r.layout.geometry.fold)
        if (columns != null) assertEquals("columns", columns, r.layout.geometry.columnCount)
        println("DM-REPORT $id cols=${r.layout.geometry.columnCount} charW=${r.layout.config.charWidthPx}")
    }

    private fun folded(
        id: String,
        slug: String,
        q: String,
        theme: ReadingThemeId,
        fold: (Win) -> FoldSpec,
        columns: Int? = null,
        fontScale: Float = 1f,
    ): Rendered {
        val r =
            render(
                "widows-orphans",
                q,
                theme,
                fontScale = fontScale,
                fold = fold,
                shot = shotName(id, slug, theme),
            )
        if (columns != null) assertEquals("columns", columns, r.layout.geometry.columnCount)
        if (fold(r.win).isSeparating) assertFoldBandClear(r) else assertNonSeparatingFoldIgnored(r)
        return r
    }

    /** LAY-14 and POS-02: a non-separating fold changes nothing. Geometry equals the no-fold geometry, ink exists. */
    private fun assertNonSeparatingFoldIgnored(r: Rendered) {
        val plain = buildLayout(parsed("widows-orphans"), r.layout.request.copy(fold = null)).geometry
        val g = r.layout.geometry
        assertEquals("column count", plain.columnCount, g.columnCount)
        assertEquals("column lefts", plain.columnLefts, g.columnLefts)
        assertEquals("column widths", plain.columnWidths, g.columnWidths)
        assertTrue("no ink at all, vacuous check", ink(r.bitmap, 0, r.bitmap.width) > 0)
        println("DM-REPORT non-separating cols=${g.columnCount} lefts=${g.columnLefts} widths=${g.columnWidths}")
    }

    private val flat = { w: Win -> verticalFold(w, halfOpened = false, occluding = false, separating = false) }
    private val book = { w: Win -> verticalFold(w, halfOpened = true, occluding = false, separating = true) }
    private val hinge = { w: Win -> verticalFold(w, halfOpened = false, occluding = true, separating = true) }

    @Test fun dm01Paper() = plain("01", "phone-portrait", Q_PHONE, ReadingThemeId.Paper, columns = 1)

    @Test fun dm01Night() = plain("01", "phone-portrait", Q_PHONE, ReadingThemeId.Night, columns = 1)

    @Test fun dm02Paper() = plain("02", "phone-landscape", Q_PHONE_LAND, ReadingThemeId.Paper)

    @Test fun dm02Night() = plain("02", "phone-landscape", Q_PHONE_LAND, ReadingThemeId.Night)

    @Test fun dm03Paper() = plain("03", "cover-screen", Q_COVER, ReadingThemeId.Paper, columns = 1)

    @Test fun dm03Night() = plain("03", "cover-screen", Q_COVER, ReadingThemeId.Night, columns = 1)

    @Test
    fun dm04Paper() {
        folded("04", "inner-flat", Q_INNER, ReadingThemeId.Paper, flat)
    }

    @Test
    fun dm04Night() {
        folded("04", "inner-flat", Q_INNER, ReadingThemeId.Night, flat)
    }

    @Test fun dm05Paper() {
        folded("05", "book", Q_INNER, ReadingThemeId.Paper, book, columns = 2)
    }

    @Test fun dm05Night() {
        folded("05", "book", Q_INNER, ReadingThemeId.Night, book, columns = 2)
    }

    @Test fun dm06Paper() {
        folded("06", "hinge-occludes", Q_INNER, ReadingThemeId.Paper, hinge, columns = 2)
    }

    @Test fun dm06Night() {
        folded("06", "hinge-occludes", Q_INNER, ReadingThemeId.Night, hinge, columns = 2)
    }

    private val tabletop = { w: Win ->
        FoldSpec(
            left = 0,
            top = w.height / 2,
            right = w.width,
            bottom = w.height / 2,
            isSeparating = true,
            orientation = FoldOrientation.Horizontal,
            halfOpened = true,
            occlusion = FoldOcclusion.None,
        )
    }

    /** Number of non-background pixels in the rows [fromY] until [toY]. */
    private fun inkRows(
        bitmap: Bitmap,
        fromY: Int,
        toY: Int,
    ): Int {
        val bg = bitmap.getPixel(0, 0)
        val row = IntArray(bitmap.width)
        var count = 0
        for (y in fromY.coerceAtLeast(0) until toY.coerceAtMost(bitmap.height)) {
            bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
            count += row.count { it != bg }
        }
        return count
    }

    private fun boundsOf(
        tag: String,
        win: Win,
    ): IntArray {
        val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pad = (win.density).toInt() + 1
        return intArrayOf(b.left.toInt() - pad, b.top.toInt() - pad, b.right.toInt() + pad, b.bottom.toInt() + pad)
    }

    private class Allowed(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun has(
            x: Int,
            y: Int,
        ) = x in left until right && y in top until bottom
    }

    private fun allowedDeckInk(
        r: Rendered,
        deck: PxRect,
    ): List<Allowed> {
        val icons =
            listOf(DECK_PREVIOUS_TAG, DECK_NEXT_TAG, DECK_TOC_TAG).map { boundsOf(it, r.win) }.map {
                Allowed(it[0], it[1], it[2], it[3])
            }
        val s = boundsOf(DECK_SCRUBBER_TAG, r.win)
        val mid = (s[1] + s[3]) / 2
        val half = (SCRUBBER_BAND_DP * r.win.density).toInt()
        val track = Allowed(s[0], mid - half, s[2], mid + half)
        val dividerBottom = s[1] + 2 * r.win.density.toInt()
        val divider = Allowed(deck.width / 2 - 3, deck.top, deck.width / 2 + 3, dividerBottom)
        return icons + track + divider
    }

    /** Counts non-background pixels in [deck]; the second value counts those outside every allowed control. */
    private fun deckInk(
        bitmap: Bitmap,
        deck: PxRect,
        allowed: List<Allowed>,
    ): Pair<Int, Int> {
        val bg = bitmap.getPixel(0, 0)
        var all = 0
        var stray = 0
        for (y in deck.top until deck.bottom) {
            for (x in deck.left until deck.right) {
                if (bitmap.getPixel(x, y) == bg) continue
                all++
                if (allowed.none { it.has(x, y) }) stray++
            }
        }
        return all to stray
    }

    /**
     * DM-07 and POS-04: text sits above the fold top edge, the deck holds controls only. The deck draws its own
     * icons, scrubber and zone divider, so deck pixels are allowed only inside the bounds of those controls: any
     * other non-background pixel in the deck rect would be page text.
     */
    private fun tabletopCase(theme: ReadingThemeId) {
        val r = render("widows-orphans", Q_INNER, theme, fold = tabletop, shot = shotName("07", "tabletop", theme))
        val g = r.layout.geometry
        assertEquals("posture", Posture.Tabletop, g.posture)
        val deck = checkNotNull(g.deckRegion) { "tabletop has no deck region" }
        assertEquals("text region bottom is the fold top", 1252, g.textRegion.bottom)
        assertEquals("deck starts at the fold bottom", 1252, deck.top)

        assertTrue("no text above the fold, vacuous check", inkRows(r.bitmap, 0, g.textRegion.bottom) > 0)
        val between = inkRows(r.bitmap, g.textRegion.bottom, deck.top)
        assertEquals("text ink between the text region and the deck", 0, between)

        val (inDeck, stray) = deckInk(r.bitmap, deck, allowedDeckInk(r, deck))
        assertTrue("deck drew nothing, vacuous check", inDeck > 0)
        assertEquals("non-control ink in the deck rect", 0, stray)
        println("DM-REPORT 07 ${theme.name} text=${g.textRegion} deck=$deck cols=${g.columnCount} deckInk=$inDeck")
    }

    @Test fun dm07Paper() = tabletopCase(ReadingThemeId.Paper)

    @Test fun dm07Night() = tabletopCase(ReadingThemeId.Night)

    @Test
    fun dm08Paper() {
        folded("08", "wide-4x3", Q_WIDE, ReadingThemeId.Paper, flat)
    }

    @Test
    fun dm08Night() {
        folded("08", "wide-4x3", Q_WIDE, ReadingThemeId.Night, flat)
    }

    @Test fun dm09Paper() = plain("09", "tablet", Q_TABLET, ReadingThemeId.Paper)

    @Test fun dm09Night() = plain("09", "tablet", Q_TABLET, ReadingThemeId.Night)

    @Test fun dm10Paper() = plain("10", "narrow-window-chrome", Q_NARROW, ReadingThemeId.Paper, 1, chrome = true)

    @Test fun dm10Night() = plain("10", "narrow-window-chrome", Q_NARROW, ReadingThemeId.Night, 1, chrome = true)

    /** DM-11: the hinge lies right of the 420 dp window, so the fold is ignored. */
    private fun split(theme: ReadingThemeId) {
        val outside = { w: Win ->
            FoldSpec(
                left = w.width + 40,
                top = 0,
                right = w.width + 40 + (DM_HINGE_DP * w.density).toInt(),
                bottom = w.height,
                isSeparating = true,
                orientation = FoldOrientation.Vertical,
                halfOpened = false,
                occlusion = FoldOcclusion.Full,
            )
        }
        val r =
            render(
                "widows-orphans",
                Q_SPLIT,
                theme,
                fold = outside,
                shot = shotName("11", "split-fold-outside", theme),
            )
        assertNull("fold outside the window is ignored", r.layout.geometry.fold)
        assertEquals(1, r.layout.geometry.columnCount)
    }

    @Test fun dm11Paper() = split(ReadingThemeId.Paper)

    @Test fun dm11Night() = split(ReadingThemeId.Night)

    /** DM-12: font scale 2.0 on the book posture; columns drop per LAY-10 and nothing clips. */
    @Test fun dm12Paper() {
        val r = folded("12", "book-font200", Q_INNER, ReadingThemeId.Paper, book, fontScale = 2f)
        assertOuterMarginsClear(r)
    }

    @Test fun dm12Night() {
        val r = folded("12", "book-font200", Q_INNER, ReadingThemeId.Night, book, fontScale = 2f)
        assertOuterMarginsClear(r)
    }

    private fun stress(
        file: String,
        fold: (Win) -> FoldSpec,
        fontScale: Float = 1f,
    ) {
        val r = render(file, Q_INNER, ReadingThemeId.Paper, fontScale = fontScale, fold = fold)
        assertFoldBandClear(r, bothSides = false)
    }

    @Test fun dm05CodeLanguages() = stress("code-languages", book)

    @Test
    fun dm05CodeLongLines() = stress("code-long-lines", book)

    @Test fun dm05TablesWide() = stress("tables-wide", book)

    @Test fun dm05TablesSimple() = stress("tables-simple", book)

    @Test fun dm06CodeLanguages() = stress("code-languages", hinge)

    @Test
    fun dm06CodeLongLines() = stress("code-long-lines", hinge)

    @Test fun dm06TablesWide() = stress("tables-wide", hinge)

    @Test fun dm06TablesSimple() = stress("tables-simple", hinge)

    @Test fun dm12CodeLongLines() = stress("code-long-lines", book, fontScale = 2f)

    @Test fun dm12TablesWide() = stress("tables-wide", book, fontScale = 2f)

    @Test fun dm12TablesSimple() = stress("tables-simple", book, fontScale = 2f)
}
