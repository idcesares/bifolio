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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ReadingMode
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

/** What the panel can do. Every action defaults to nothing so a preview or a screenshot test can omit them. */
internal data class ChromeActions(
    val onBack: () -> Unit = {},
    val onOpenAppearance: () -> Unit = {},
    val onMode: (ReadingMode) -> Unit = {},
    val onAppearance: ((AppearanceProfile) -> AppearanceProfile) -> Unit = {},
)

/**
 * Reader chrome (UI-03), shown by a center tap and hidden by another or after 8 s without use. A floating panel
 * over the page, without the title: back to the library, quick columns, theme and text size, the pages or scroll
 * switch, and the full appearance sheet (UI-10). Any use of a control restarts the countdown.
 *
 * The panel is built from equal, fixed slots with a fixed text size, so nothing moves when a value or the
 * reading size changes. The table of contents, search, scrubber, page label and time remaining arrive with M5.
 *
 * Motion (MOT-06): the panel fades and slides in on an expressive spring with slight overshoot, and leaves faster
 * without overshoot. Reduced motion is a plain fade (MOT-07). Both can be interrupted at any point (MOT-09).
 */
@Composable
internal fun ReaderChrome(
    visible: Boolean,
    modifier: Modifier = Modifier,
    appearance: AppearanceProfile = AppearanceProfile(),
    mode: ReadingMode = ReadingMode.PAGED,
    actions: ChromeActions = ChromeActions(),
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
            ToolbarPanel(appearance, mode, actions)
        }
    }
}

private const val CHROME_TRAVEL = 3
private val PanelRadius = 28.dp
private val PanelElevation = 8.dp
private val PanelMaxWidth = 520.dp
private val SlotHeight = 56.dp
private val SlotRadius = 20.dp
private val IconSlot = 24.dp

/** Fixed size, whatever the reading size is: the chrome never follows the text. */
private val GlyphSize = 16.sp
private val CaptionSize = 11.sp
private val CaptionLine = 14.sp

/**
 * The toolbar slots are about 48dp wide and 56dp tall whatever the font scale, so their text follows the system scale
 * only up to this factor. Past it a caption would break inside its word (A11Y-03); every control keeps its full
 * description for TalkBack, and the appearance sheet scales without limit.
 */
private const val TOOLBAR_MAX_FONT_SCALE = 1.2f

/**
 * The few settings that change often, one tap each on the same global profile as the appearance sheet. The panel
 * sits on the tonal surface of the theme with a hairline edge and a soft shadow, so it reads as a layer over the
 * page and not as part of it. Everything else stays in the sheet.
 */
@Composable
private fun ToolbarPanel(
    appearance: AppearanceProfile,
    mode: ReadingMode,
    actions: ChromeActions,
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
    val pages = mode == ReadingMode.PAGED
    val modeName = stringResource(if (pages) R.string.reader_quick_mode_pages else R.string.reader_quick_mode_scroll)
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
                .padding(horizontal = Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarItem(
            description = stringResource(R.string.reader_chrome_back),
            onClick = actions.onBack,
            icon = BifolioIcons.ChevronLeft,
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_columns, columns),
            onClick = { actions.onAppearance { it.withNextColumns() } },
            icon = BifolioIcons.Columns2,
            caption = columns,
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_theme, theme),
            onClick = { actions.onAppearance { it.withNextTheme() } },
            icon = BifolioIcons.Contrast,
            caption = theme,
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_smaller),
            onClick = { actions.onAppearance { it.withBodySpStep(-1) } },
            glyph = stringResource(R.string.reader_quick_smaller_label),
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_larger),
            onClick = { actions.onAppearance { it.withBodySpStep(1) } },
            glyph = stringResource(R.string.reader_quick_larger_label),
        )
        ToolbarItem(
            description = stringResource(R.string.reader_quick_mode, modeName),
            onClick = { actions.onMode(if (pages) ReadingMode.SCROLL else ReadingMode.PAGED) },
            icon = if (pages) BifolioIcons.BookOpen else BifolioIcons.ScrollText,
            caption = modeName,
        )
        ToolbarItem(
            description = stringResource(R.string.reader_chrome_appearance),
            onClick = actions.onOpenAppearance,
            icon = BifolioIcons.SlidersHorizontal,
            accent = true,
        )
    }
}

/**
 * One control of the panel in a slot of equal width and fixed height: an icon or a short glyph, and optionally the
 * current value under it. The value cross-fades when it changes and never changes the size of the slot.
 */
@Composable
private fun RowScope.ToolbarItem(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    glyph: String? = null,
    caption: String? = null,
    accent: Boolean = false,
) {
    val palette = BifolioTheme.palette
    val tint = if (accent) palette.accent else palette.ink
    val text = BifolioTheme.typography.caption.copy(color = tint, fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    val capped = remember(density) { Density(density.density, minOf(density.fontScale, TOOLBAR_MAX_FONT_SCALE)) }
    CompositionLocalProvider(LocalDensity provides capped) {
        ToolbarSlot(description, onClick, icon, glyph, caption, tint, text)
    }
}

@Composable
private fun RowScope.ToolbarSlot(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector?,
    glyph: String?,
    caption: String?,
    tint: Color,
    text: TextStyle,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .height(SlotHeight)
                .clip(RoundedCornerShape(SlotRadius))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) BifolioIcon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSlot))
        if (glyph != null) {
            BasicText(text = glyph, maxLines = 1, style = text.copy(fontSize = GlyphSize, lineHeight = GlyphSize))
        }
        if (caption != null) {
            Crossfade(targetState = caption, animationSpec = BifolioMotion.fade(), label = "toolbarCaption") { value ->
                BasicText(
                    text = value,
                    maxLines = 1,
                    style = text.copy(fontSize = CaptionSize, lineHeight = CaptionLine),
                )
            }
        }
    }
}
