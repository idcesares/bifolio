package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.core.animate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.model.Anchor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the anchor line is on screen: the window position of the left edge of its column at the line's top, the
 * line height and the column width. [request] names the layout it was measured in.
 */
internal class AnchorSpot(
    val request: PageRequest,
    val x: Float,
    val y: Float,
    val height: Float,
    val width: Float,
)

/**
 * Continuity motion (MOT-05, ANC-03). When the geometry changes, the new spread is shown at once with the anchor
 * kept unchanged; the spread then glides so that the anchor line travels from where it was on screen to where it
 * is now over [BifolioMotion.CONTINUITY_DURATION_MILLIS], and a soft highlight on that line fades over
 * [BifolioMotion.ANCHOR_GLOW_DURATION_MILLIS]. Both ends are on screen, so the line stays visible all the way.
 *
 * Nothing here blocks input and nothing writes the anchor (MOT-09). With [reduced] set the glide is skipped and
 * only the highlight remains (MOT-07). A second change during the glide starts from the position the line has
 * reached, so it never jumps. Offsets and glow are read from draw code only.
 */
internal class ContinuityMotion(
    private val scope: CoroutineScope,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Translation in px of the whole spread while the anchor line glides. */
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** Opacity of the anchor highlight, 0 when idle. */
    var glow by mutableFloatStateOf(0f)
        private set

    /** The anchor line in the current layout, null until one was measured. */
    var spot: AnchorSpot? = null
        private set

    private var lastSpot: AnchorSpot? = null
    private var moveJob: Job? = null
    private var glowJob: Job? = null

    /** True while the glide is running. */
    val gliding: Boolean get() = moveJob?.isActive == true

    /**
     * Measures where [shown] sits in [layout] and, when [layout] answers a different request than the previous one,
     * starts the motion once [ready] returns (the new spread has been prepared). The effect that calls this may be
     * cancelled by newer layouts; the motion itself runs in the surface scope and survives that.
     */
    suspend fun track(
        layout: PagedLayout,
        shown: Anchor,
        originX: Float,
        originY: Float,
        reduced: Boolean,
        ready: suspend () -> Unit,
    ) {
        val now = withContext(compute) { spotOf(layout, shown, originX, originY) } ?: return
        val before = lastSpot
        if (before != null && before.request != layout.request) {
            ready()
            begin(before, now, reduced)
        }
        lastSpot = now
        spot = now
    }

    /** Stops the glide where it stands and settles the spread at rest. The highlight keeps fading. */
    fun cancel() {
        moveJob?.cancel()
        moveJob = null
        offsetX = 0f
        offsetY = 0f
    }

    private fun begin(
        from: AnchorSpot,
        to: AnchorSpot,
        reduced: Boolean,
    ) {
        // Where the line is drawn right now: its old place plus whatever glide was still running.
        val startX = from.x + offsetX - to.x
        val startY = from.y + offsetY - to.y
        moveJob?.cancel()
        glowJob?.cancel()
        spot = to
        if (reduced) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX = startX
            offsetY = startY
            moveJob =
                scope.launch {
                    animate(1f, 0f, animationSpec = BifolioMotion.continuity()) { left, _ ->
                        offsetX = startX * left
                        offsetY = startY * left
                    }
                    offsetX = 0f
                    offsetY = 0f
                }
        }
        glow = BifolioMotion.ANCHOR_GLOW_ALPHA
        glowJob =
            scope.launch {
                animate(BifolioMotion.ANCHOR_GLOW_ALPHA, 0f, animationSpec = BifolioMotion.anchorGlow()) { value, _ ->
                    glow = value
                }
                glow = 0f
            }
    }

    private fun spotOf(
        layout: PagedLayout,
        shown: Anchor,
        originX: Float,
        originY: Float,
    ): AnchorSpot? {
        val flow = layout.flow
        val pageIndex = flow.pageIndexOf(shown)
        if (pageIndex < 0) return null
        val column = pageIndex % flow.columnCount
        val geometry = layout.geometry
        val line = layout.detailer.anchorLine(flow.pages[pageIndex], column, shown)
        return AnchorSpot(
            request = layout.request,
            x = originX + geometry.columnLefts[column],
            y = originY + geometry.verticalMargin + line.y,
            height = line.height.toFloat(),
            width = geometry.columnWidths[column].toFloat(),
        )
    }
}
