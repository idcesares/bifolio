package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ThemeChoice
import dev.dcesares.bifolio.data.withBodySpStep
import dev.dcesares.bifolio.data.withNextColumns
import dev.dcesares.bifolio.data.withNextTheme
import dev.dcesares.bifolio.designsystem.BifolioIcon
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
 * Reader chrome (UI-03), shown by a center tap and hidden by another or after 8 s without use. A floating panel
 * over the page, without the title: quick columns, theme and text size, and a labeled control that opens the full
 * appearance sheet (UI-10). Any use of a control restarts the countdown.
 *
 * The table of contents, search, scrubber, page label and time remaining arrive with M5.
 *
 * Motion (MOT-06): the panel fades and slides in on an expressive spring with slight overshoot, and leaves faster
 * without overshoot. Reduced motion is a plain fade (MOT-07). Both can be interrupted at any point (MOT-09).
 */
@Composable
internal fun ReaderChrome(
    visible: Boolean,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: AppearanceProfile = AppearanceProfile(),
    onAppearance: ((AppearanceProfile) -> AppearanceProfile) -> Unit = {},
) {
    val reduced = rememberReducedMotion()
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
        Box(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = insets.calculateStartPadding(direction) + Spacing.S,
                    end = insets.calculateEndPadding(direction) + Spacing.S,
                    top = insets.calculateTopPadding() + Spacing.S,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            ToolbarPanel(appearance, onAppearance, onOpenAppearance)
        }
    }
}

private const val CHROME_TRAVEL = 3
private val PanelRadius = 28.dp
private val PanelElevation = 8.dp
private val PanelMaxWidth = 520.dp
private val ItemRadius = 20.dp
private val DividerHeight = 24.dp

/**
 * The few settings that change often, one tap each on the same global profile as the appearance sheet. The panel
 * sits on the tonal surface of the theme with a hairline edge and a soft shadow, so it reads as a layer over the
 * page and not as part of it. Everything else stays in the sheet.
 */
@Composable
private fun ToolbarPanel(
    appearance: AppearanceProfile,
    onAppearance: ((AppearanceProfile) -> AppearanceProfile) -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val shape = RoundedCornerShape(PanelRadius)
    val columns = appearance.columns?.toString() ?: stringResource(R.string.reader_quick_columns_auto)
    val theme =
        stringResource(
            when (appearance.theme) {
                null -> R.string.reader_quick_theme_auto
                ThemeChoice.PAPER -> R.string.reader_quick_theme_paper
                ThemeChoice.SEPIA -> R.string.reader_quick_theme_sepia
                ThemeChoice.DUSK -> R.string.reader_quick_theme_dusk
                ThemeChoice.NIGHT -> R.string.reader_quick_theme_night
            },
        )
    Row(
        modifier =
            Modifier
                .widthIn(max = PanelMaxWidth)
                .fillMaxWidth()
                .shadow(PanelElevation, shape)
                .background(palette.codeSurface, shape)
                .border(Spacing.Rule, palette.rule, shape)
                // The panel sits over the page: its taps must not turn pages or hide the chrome.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = Spacing.S, vertical = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ToolbarItem(
            description = stringResource(R.string.reader_quick_columns, columns),
            onClick = { onAppearance { it.withNextColumns() } },
            icon = BifolioIcons.Columns2,
            label = columns,
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_theme, theme),
            onClick = { onAppearance { it.withNextTheme() } },
            icon = BifolioIcons.Contrast,
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_smaller),
            onClick = { onAppearance { it.withBodySpStep(-1) } },
            label = stringResource(R.string.reader_quick_smaller_label),
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_larger),
            onClick = { onAppearance { it.withBodySpStep(1) } },
            label = stringResource(R.string.reader_quick_larger_label),
        )
        Box(Modifier.width(Spacing.Rule).height(DividerHeight).background(palette.rule))
        ToolbarItem(
            description = stringResource(R.string.reader_chrome_appearance),
            onClick = onOpenAppearance,
            icon = BifolioIcons.Type,
            label = stringResource(R.string.reader_chrome_appearance),
            accent = true,
        )
    }
}

/** One control of the panel: an icon, a short label or both. The label cross-fades when its value changes. */
@Composable
private fun ToolbarItem(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    label: String? = null,
    accent: Boolean = false,
) {
    val palette = BifolioTheme.palette
    val tint = if (accent) palette.accent else palette.ink
    Row(
        modifier =
            Modifier
                .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
                .clip(RoundedCornerShape(ItemRadius))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs, Alignment.CenterHorizontally),
    ) {
        if (icon != null) BifolioIcon(icon, contentDescription = null, tint = tint)
        if (label != null) {
            Crossfade(targetState = label, animationSpec = BifolioMotion.fade(), label = "toolbarLabel") { text ->
                BasicText(
                    text = text,
                    maxLines = 1,
                    style = BifolioTheme.typography.caption.copy(color = tint, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
