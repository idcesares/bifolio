package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing

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
 * until M4: the title, and one control for the reading mode.
 *
 * TEMPORARY: the mode control stands in for the appearance sheet (UI-10, M4) and goes away with it. The table of
 * contents, search, appearance, scrubber, page label and time remaining arrive with M4 and M5.
 *
 * Motion is a plain fade (`motion.fade`, also the reduced-motion path); the expressive chrome springs of
 * DESIGN section 6 join with Material 3 in M4. Fading in and out can be interrupted at any point.
 */
@Composable
internal fun ReaderChrome(
    visible: Boolean,
    title: String,
    mode: ReadingMode,
    canSwitchMode: Boolean,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val insets = ChromeInsets.asPaddingValues()
    val direction = LocalLayoutDirection.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(BifolioMotion.fade()),
        exit = fadeOut(BifolioMotion.fade()),
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
                if (canSwitchMode) {
                    val paged = mode == ReadingMode.PAGED
                    val label =
                        if (paged) R.string.reader_chrome_switch_to_scroll else R.string.reader_chrome_switch_to_pages
                    BifolioIconButton(
                        icon = if (paged) BifolioIcons.ChevronsUpDown else BifolioIcons.BookOpen,
                        description = stringResource(label),
                        onClick = onSwitchMode,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(Spacing.Rule).background(palette.rule))
        }
    }
}
