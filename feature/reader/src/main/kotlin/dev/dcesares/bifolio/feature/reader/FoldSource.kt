package dev.dcesares.bifolio.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import dev.dcesares.bifolio.layout.FoldOcclusion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map

/**
 * Where the folding feature comes from (LAY-02, ADR-0010). Emits the first folding feature of the window as a
 * [FoldSpec] in **window coordinates** (the platform's own bounds, before any inset translation), or null when
 * there is none. Whether it intersects the content area is decided later by the geometry rule (LAY-01).
 */
internal interface FoldSource {
    val fold: Flow<FoldSpec?>

    /** The fold known before the first emission, so a source that already knows it does not cost a relayout. */
    val initialFold: FoldSpec? get() = null

    /** No fold, ever: phones, cover screens, previews. */
    object None : FoldSource {
        override val fold: Flow<FoldSpec?> = MutableStateFlow(null)
    }
}

/** Test and preview source: holds a fold that the caller can change to simulate hinge movement. */
internal class FakeFoldSource(
    initial: FoldSpec? = null,
) : FoldSource {
    private val state = MutableStateFlow(initial)
    override val fold: Flow<FoldSpec?> = state
    override val initialFold: FoldSpec? get() = state.value

    fun emit(spec: FoldSpec?) {
        state.value = spec
    }
}

/** Production source: `WindowInfoTracker.windowLayoutInfo` of the hosting activity (LAY-02). */
internal class WindowManagerFoldSource(
    private val activity: Activity,
) : FoldSource {
    override val fold: Flow<FoldSpec?> =
        WindowInfoTracker
            .getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info ->
                info.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?.toFoldSpec()
            }
}

private fun FoldingFeature.toFoldSpec(): FoldSpec =
    foldSpecOf(
        halfOpened = state == FoldingFeature.State.HALF_OPENED,
        vertical = orientation == FoldingFeature.Orientation.VERTICAL,
        fullOcclusion = occlusionType == FoldingFeature.OcclusionType.FULL,
        separating = isSeparating,
        left = bounds.left,
        top = bounds.top,
        right = bounds.right,
        bottom = bounds.bottom,
    )

/**
 * Pure mapping from the `FoldingFeature` fields to a [FoldSpec] (POS-03). `OcclusionType.FULL` implies a
 * separating fold, so it is enforced here even if a source forgets it. Bounds are normalized, never inverted.
 */
@Suppress("LongParameterList") // One argument per FoldingFeature field, kept primitive so the JVM can test it.
internal fun foldSpecOf(
    halfOpened: Boolean,
    vertical: Boolean,
    fullOcclusion: Boolean,
    separating: Boolean,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
): FoldSpec =
    FoldSpec(
        left = minOf(left, right),
        top = minOf(top, bottom),
        right = maxOf(left, right),
        bottom = maxOf(top, bottom),
        isSeparating = separating || fullOcclusion,
        orientation = if (vertical) FoldOrientation.Vertical else FoldOrientation.Horizontal,
        halfOpened = halfOpened,
        occlusion = if (fullOcclusion) FoldOcclusion.Full else FoldOcclusion.None,
    )

/**
 * Moves a fold from window coordinates into the coordinates of the content area, whose origin is the top left
 * corner left after the safe-drawing insets (LAY-01, ADR-0010). The result may lie partly or fully outside the
 * area; the geometry rule ignores a fold that does not intersect it.
 */
internal fun FoldSpec.translatedBy(
    insetLeftPx: Int,
    insetTopPx: Int,
): FoldSpec =
    copy(
        left = left - insetLeftPx,
        top = top - insetTopPx,
        right = right - insetLeftPx,
        bottom = bottom - insetTopPx,
    )

/** The activity behind [this], or null when the context is not hosted by one. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** The platform source for the hosting activity, or [FoldSource.None] when there is none (previews). */
@Composable
internal fun rememberFoldSource(): FoldSource {
    val context = LocalContext.current
    return remember(context) { context.findActivity()?.let(::WindowManagerFoldSource) ?: FoldSource.None }
}

/** Debounce of request bursts (a hinge sweeping through angles), in milliseconds. The first request is not delayed. */
internal const val REQUEST_DEBOUNCE_MILLIS = 120L

/**
 * Emits the first value at once and afterwards only the value that stays unchanged for [millis]. A burst
 * collapses to its last element, and a delayed value is dropped as soon as a newer one arrives, so stale
 * paginations are never started.
 */
internal fun <T> Flow<T>.debounceAfterFirst(millis: Long): Flow<T> =
    channelFlow {
        var first = true
        collectLatest { value ->
            if (first) {
                first = false
            } else {
                delay(millis)
            }
            send(value)
        }
    }
