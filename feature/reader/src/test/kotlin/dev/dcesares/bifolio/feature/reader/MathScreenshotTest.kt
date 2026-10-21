package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.Paginator
import dev.dcesares.bifolio.math.rememberMathEngine
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val MATH_PHONE = "w412dp-h915dp-xhdpi"
private const val MATH_WAIT_MILLIS = 60_000L
private const val C14 = "math-inline-display"
private const val C15 = "math-errors"

/** What the view model does for a request: the sizes of one snapshot reach the paginator and the detailer. */
private fun mathLayout(
    document: Document,
    request: PageRequest,
    session: MathSession,
): PagedLayout {
    val (math, key) = session.sizes.value.forRequest(request)
    val text = AndroidTextEngine().prepare(request)
    val geometry = GeometryCalculator.compute(text.spec)
    val flow = Paginator(document, text.measurer, geometry, math = math).paginateAll()!!
    return PagedLayout(
        request,
        geometry,
        text.config,
        flow,
        PageDetailer(document, text.measurer, geometry, math = math),
        0,
        key,
    )
}

@Composable
private fun MathPagedHarness(
    document: Document,
    session: MathSession,
    spread: Int,
    onLayout: (PagedLayout) -> Unit,
) {
    val engine = rememberMathEngine()
    val bodySp = BifolioTheme.typography.body.fontSize.value
    LaunchedEffect(engine, bodySp) { session.bind(engine, bodySp) }
    val snapshot by session.sizes.collectAsState()
    var layout by remember { mutableStateOf<PagedLayout?>(null) }
    var request by remember { mutableStateOf<PageRequest?>(null) }
    var start by remember { mutableStateOf(Anchor(0, 0)) }
    // Sizes arrive after the first layout: repaginate from the anchor, as the view model does (PAG-07, ANC-03).
    LaunchedEffect(request, snapshot.key) {
        val asked = request ?: return@LaunchedEffect
        val built = mathLayout(document, asked, session)
        layout = built
        onLayout(built)
        if (spread > 0 && start == Anchor(0, 0)) {
            start = built.flow.pages[(spread * built.flow.columnCount).coerceAtMost(built.flow.pages.lastIndex)].anchor
        }
    }
    Box(Modifier.fillMaxSize()) {
        key(start) {
            PagedSurface(
                layout = layout,
                initialAnchor = start,
                onRequest = { request = it },
                onSettled = {},
                onFirstDrawn = {},
                onToggleChrome = {},
                math = session.renderer,
            )
        }
    }
}

/** SYN-12 (display math drawn, failures show their source), PAG-07 (atomic box), A11Y-03, corpus C-14 and C-15. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MathScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun session(document: Document) =
        MathSession(document, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /**
     * Waits for the state the shot stands for, with no sleeping: every formula is measured, the layout is the one made
     * from those sizes, drawings were started when any formula was sized, none is in flight, and the number of started
     * drawings did not move over one more frame. Capturing the root each round is what makes the surface draw.
     */
    private fun waitForPixels(
        session: MathSession,
        expectsDrawings: Boolean,
        ready: () -> Boolean = { true },
    ) {
        var seen = -1
        compose.waitUntil(MATH_WAIT_MILLIS) {
            compose.onRoot().captureToImage()
            val quiet = session.settled && ready() && session.renderer.idle()
            val requested = session.renderer.requested
            val stable = quiet && requested == seen && (requested > 0 || !expectsDrawings)
            seen = requested
            stable
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        compose.waitForIdle()
    }

    private fun paged(
        shotName: String,
        corpus: String = C14,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        fontScale: Float = 1f,
        spread: Int = 0,
        capture: Boolean = true,
    ): MathSession {
        RuntimeEnvironment.setQualifiers("+$MATH_PHONE")
        RuntimeEnvironment.setFontScale(fontScale)
        val document = parsed(corpus)
        val session = session(document)
        val built = java.util.concurrent.CopyOnWriteArrayList<PagedLayout>()
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) { MathPagedHarness(document, session, spread) { built.add(it) } }
            }
        }
        // The layout that counts is the one made from the sizes now known (the math key of the latest snapshot).
        waitForPixels(session, expectsDrawings = corpus == C14) {
            built.lastOrNull()?.let {
                it.mathKey ==
                    session.sizes.value
                        .forRequest(it.request)
                        .second
            } == true
        }
        if (capture) compose.onRoot().captureRoboImage("src/test/screenshots/$shotName.png")
        return session
    }

    private fun scroll(
        shotName: String,
        corpus: String = C14,
        theme: ReadingThemeId = ReadingThemeId.Paper,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setQualifiers("+$MATH_PHONE")
        RuntimeEnvironment.setFontScale(fontScale)
        val document = parsed(corpus)
        val session = session(document)
        val ready = ReaderUiState.Ready(document, "Math", "math", emptySet(), Anchor(0, 0))
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                BifolioTheme(theme) {
                    val engine = rememberMathEngine()
                    val bodySp = BifolioTheme.typography.body.fontSize.value
                    LaunchedEffect(engine, bodySp) { session.bind(engine, bodySp) }
                    ReaderScreen(ready, {}, math = session.renderer)
                }
            }
        }
        waitForPixels(session, expectsDrawings = corpus == C14)
        compose.onRoot().captureRoboImage("src/test/screenshots/$shotName.png")
    }

    @Test
    fun pagedPaper() {
        paged("math-paged-paper-phone")
    }

    @Test
    fun pagedPaperLaterSpread() {
        paged("math-paged-paper-phone-s3", spread = 3)
    }

    @Test
    fun pagedNight() {
        paged("math-paged-night-phone", theme = ReadingThemeId.Night)
    }

    @Test
    fun pagedLargeText() {
        paged("math-paged-paper-phone-200-s5", fontScale = 2f, spread = 5)
    }

    @Test
    fun pagedFailures() {
        paged("math-errors-paged-paper-phone", corpus = C15)
    }

    @Test
    fun pagedFormulasAreReadAsTheirSource() {
        val session = paged("semantics", capture = false)
        val tex = session.blocks.first().tex
        compose.onNodeWithContentDescription(tex, useUnmergedTree = true).assertExists()
    }

    @Test
    fun pagedFailuresAreReadAsFailureAndSource() {
        val session = paged("semantics-failed", corpus = C15, capture = false)
        val failedText = RuntimeEnvironment.getApplication().getString(R.string.reader_math_failed)
        val failed = session.blocks.filter { session.renderer.hasFailed(it) }
        assertTrue(failed.isNotEmpty())
        val described =
            compose.onAllNodes(hasContentDescriptionStartingWith(failedText), useUnmergedTree = true)
        assertTrue(described.fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("$failedText ${failed.first().tex}", useUnmergedTree = true).assertExists()
    }

    private fun hasContentDescriptionStartingWith(prefix: String) =
        SemanticsMatcher("content description starts with $prefix") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.startsWith(prefix) } == true
        }

    @Test
    fun scrollPaper() = scroll("math-scroll-paper-phone")

    @Test
    fun scrollNight() = scroll("math-scroll-night-phone", theme = ReadingThemeId.Night)

    @Test
    fun scrollFailures() = scroll("math-errors-scroll-paper-phone", corpus = C15)
}
