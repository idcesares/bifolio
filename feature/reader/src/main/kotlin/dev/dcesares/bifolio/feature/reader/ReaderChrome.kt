package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.rememberReducedMotion

/**
 * Insets the reading surfaces lay out against: the display cutout only. The system bars are hidden while
 * reading (UI-04) and overlay the page when they come back with the chrome, so they take no room from the text
 * and toggling the chrome never changes the page geometry or repaginates. Text never sits under a camera cutout.
 */
internal val ReaderInsets: WindowInsets
    @Composable
    get() = WindowInsets.displayCutout

/**
 * Insets the chrome itself clears: the system bars at their full size (visible or not, so the bar does not jump
 * when they appear) plus the cutout.
 */
internal val ChromeInsets: WindowInsets
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    get() = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)

/**
 * Reader chrome (UI-03), shown by a center tap and hidden by another or after 4 s. Only the top bar exists
 * until M5: the title, and the control that opens the appearance sheet (UI-10).
 *
 * The table of contents, search, scrubber, page label and time remaining arrive with M4 and M5.
 *
 * Motion (MOT-06): the bar fades and slides in on an expressive spring with slight overshoot, and leaves faster
 * without overshoot. Reduced motion is a plain fade (MOT-07). Both can be interrupted at any point (MOT-09).
 */
@Composable
internal fun ReaderChrome(
    visible: Boolean,
    title: String,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val palette = BifolioTheme.palette
    val insets = ChromeInsets.asPaddingValues()
    val direction = LocalLayoutDirection.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            if (reduced) {
                fadeIn(BifolioMotion.fade())
            } else {
                fadeIn(BifolioMotion.fade()) +
                    slideInVertically(BifolioMotion.chromeEnter()) { -it / CHROME_TRAVEL }
            },
        exit =
            if (reduced) {
                fadeOut(BifolioMotion.fade())
            } else {
                fadeOut(BifolioMotion.chromeExit()) +
                    slideOutVertically(BifolioMotion.chromeExit()) { -it / CHROME_TRAVEL }
            },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.page)
                // The bar sits over the page: its taps must not turn pages or hide the chrome.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = insets.calculateStartPadding(direction) + Spacing.L,
                            end = insets.calculateEndPadding(direction) + Spacing.S,
                            top = insets.calculateTopPadding(),
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = BifolioTheme.typography.caption.copy(color = palette.ink),
                )
                BifolioIconButton(
                    icon = BifolioIcons.Type,
                    description = stringResource(R.string.reader_chrome_appearance),
                    onClick = onOpenAppearance,
                )
                Box(Modifier.fillMaxWidth().height(Spacing.Rule).background(palette.rule))
            }
        }
    }
}

private const val CHROME_TRAVEL = 3
