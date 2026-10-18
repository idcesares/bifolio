package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing

private const val MINUTES_PER_HOUR = 60

@Composable
private fun duration(minutes: Int): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.reader_duration_minutes, minutes)
    } else {
        stringResource(R.string.reader_duration_hours, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

/**
 * Bottom strip of the chrome (UI-03): the percent read and the time left in the document (UI-06). It shows and
 * hides with the top bar. The scrubber and the page label join it with M5.
 */
@Composable
internal fun ReaderFooter(
    visible: Boolean,
    timeLeft: TimeLeft?,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val insets = ChromeInsets.asPaddingValues()
    val direction = LocalLayoutDirection.current
    AnimatedVisibility(
        visible = visible && timeLeft != null,
        modifier = modifier,
        enter = fadeIn(BifolioMotion.fade()),
        exit = fadeOut(BifolioMotion.fade()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.page)
                // The strip sits over the page: its taps must not turn pages or hide the chrome.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(Modifier.fillMaxWidth().height(Spacing.Rule).background(palette.rule))
            timeLeft?.let {
                BasicText(
                    text = stringResource(R.string.reader_time_left, it.percent, duration(it.documentMinutes)),
                    modifier =
                        Modifier.padding(
                            start = insets.calculateStartPadding(direction) + Spacing.L,
                            end = insets.calculateEndPadding(direction) + Spacing.L,
                            top = Spacing.S,
                            bottom = insets.calculateBottomPadding() + Spacing.S,
                        ),
                    style = BifolioTheme.typography.caption.copy(color = palette.inkMuted),
                )
            }
        }
    }
}
