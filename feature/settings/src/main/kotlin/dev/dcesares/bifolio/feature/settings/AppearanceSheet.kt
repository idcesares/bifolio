package dev.dcesares.bifolio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.LINE_HEIGHTS
import dev.dcesares.bifolio.data.MARGIN_SCALES
import dev.dcesares.bifolio.data.MAX_BODY_SP
import dev.dcesares.bifolio.data.MIN_BODY_SP
import dev.dcesares.bifolio.data.ReadingMode
import dev.dcesares.bifolio.data.ThemeChoice
import dev.dcesares.bifolio.data.TurnChoice
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.DynamicPalette
import dev.dcesares.bifolio.designsystem.Spacing

private val SheetRadius = 12.dp
private val SheetMaxWidth = 560.dp
private val SidePaneMinWindow = 840.dp
private val SidePaneWidth = 400.dp
private val SizeValueMinWidth = 56.dp
private const val SCRIM_ALPHA = 0.32f
private const val SHEET_MAX_HEIGHT = 0.85f

/** What the sheet edits besides the profile: the reading mode and the volume keys live in other stores. */
public data class SheetState(
    val profile: AppearanceProfile,
    val mode: ReadingMode,
    val volumeKeysTurn: Boolean,
    /** Columns the current window fits, for the note under the columns choice. Null hides the note. */
    val fittingColumns: Int? = null,
)

/** Callbacks of the sheet. The profile change is a function so rapid taps never overwrite each other. */
public class SheetActions(
    public val onProfile: ((AppearanceProfile) -> AppearanceProfile) -> Unit,
    public val onMode: (ReadingMode) -> Unit,
    public val onVolumeKeys: (Boolean) -> Unit,
    public val onDismiss: () -> Unit,
)

/**
 * Appearance sheet (UI-10): every reading choice in one place, applied live. The profile is global.
 * It sits over a scrim; a tap on the scrim closes it.
 */
@Composable
public fun AppearanceSheet(
    state: SheetState,
    actions: SheetActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .pointerInput(Unit) { detectTapGestures { actions.onDismiss() } },
    ) {
        // UI-02: from 840dp the sheet becomes a side pane at the end edge instead of a bottom sheet.
        val side = maxWidth >= SidePaneMinWindow
        val shape =
            if (side) {
                RoundedCornerShape(topStart = SheetRadius, bottomStart = SheetRadius)
            } else {
                RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius)
            }
        Column(
            Modifier
                .align(if (side) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(
                    if (side) {
                        Modifier
                            .width(
                                SidePaneWidth,
                            ).fillMaxHeight()
                    } else {
                        Modifier
                            .widthIn(
                                max = SheetMaxWidth,
                            ).fillMaxWidth()
                            .fillMaxHeight(SHEET_MAX_HEIGHT)
                    },
                ).clip(shape)
                .background(BifolioTheme.palette.page)
                // Taps inside the sheet must not reach the scrim.
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            SheetHeader(actions.onDismiss)
            AppearanceSections(state, actions)
        }
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = stringResource(R.string.appearance_title),
            modifier = Modifier.weight(1f),
            style = BifolioTheme.typography.headings[1].copy(color = BifolioTheme.palette.ink),
        )
        BifolioIconButton(
            icon = BifolioIcons.ChevronDown,
            description = stringResource(R.string.appearance_close),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun AppearanceSections(
    state: SheetState,
    actions: SheetActions,
) {
    val p = state.profile
    val change = actions.onProfile
    ThemeSection(p.theme) { choice -> change { it.copy(theme = choice) } }
    FontSection(p.font) { font -> change { it.copy(font = font) } }
    SizeSection(p.bodySp) { sp -> change { it.copy(bodySp = sp) } }
    LineHeightSection(p.lineHeight) { value -> change { it.copy(lineHeight = value) } }
    MarginSection(p.marginScale) { value -> change { it.copy(marginScale = value) } }
    ColumnsSection(p.columns, state.fittingColumns) { count -> change { it.copy(columns = count) } }
    ToggleRow(stringResource(R.string.appearance_justify), p.justify) { on -> change { it.copy(justify = on) } }
    ToggleRow(stringResource(R.string.appearance_hyphenate), p.hyphenate) { on -> change { it.copy(hyphenate = on) } }
    ModeSection(state.mode, actions.onMode)
    TurnSection(p.turn) { turn -> change { it.copy(turn = turn) } }
    ToggleRow(stringResource(R.string.appearance_hairline), p.hairline) { on -> change { it.copy(hairline = on) } }
    ToggleRow(stringResource(R.string.appearance_volume_keys), state.volumeKeysTurn, actions.onVolumeKeys)
    ToggleRow(stringResource(R.string.appearance_reduce_motion), p.reduceMotion) { on ->
        change { it.copy(reduceMotion = on) }
    }
    // TYP-09: moves to the settings screen with M6; the reading surface never uses it.
    if (DynamicPalette.isAvailable) {
        ToggleRow(stringResource(R.string.appearance_dynamic_color), p.dynamicColor) { on ->
            change { it.copy(dynamicColor = on) }
        }
    }
}

@Composable
private fun ThemeSection(
    selected: ThemeChoice?,
    onSelect: (ThemeChoice?) -> Unit,
) {
    val options =
        listOf<Pair<ThemeChoice?, String>>(
            null to stringResource(R.string.appearance_theme_auto),
            ThemeChoice.PAPER to stringResource(R.string.appearance_theme_paper),
            ThemeChoice.SEPIA to stringResource(R.string.appearance_theme_sepia),
            ThemeChoice.DUSK to stringResource(R.string.appearance_theme_dusk),
            ThemeChoice.NIGHT to stringResource(R.string.appearance_theme_night),
        )
    Choice(stringResource(R.string.appearance_theme), options, selected, onSelect)
}

@Composable
private fun SizeSection(
    sp: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(stringResource(R.string.appearance_size), Modifier.weight(1f))
        StepButton("−", stringResource(R.string.appearance_size_smaller), sp > MIN_BODY_SP) { onChange(sp - 1) }
        BasicText(
            text = stringResource(R.string.appearance_size_value, sp),
            modifier = Modifier.defaultMinSize(minWidth = SizeValueMinWidth),
            style = BifolioTheme.typography.caption.copy(color = BifolioTheme.palette.ink),
        )
        StepButton("+", stringResource(R.string.appearance_size_larger), sp < MAX_BODY_SP) { onChange(sp + 1) }
    }
}

@Composable
private fun LineHeightSection(
    value: Float,
    onSelect: (Float) -> Unit,
) {
    val names =
        listOf(
            stringResource(R.string.appearance_line_tight),
            stringResource(R.string.appearance_line_normal),
            stringResource(R.string.appearance_line_loose),
        )
    Choice(stringResource(R.string.appearance_line_height), LINE_HEIGHTS.zip(names), value, onSelect)
}

@Composable
private fun MarginSection(
    value: Float,
    onSelect: (Float) -> Unit,
) {
    val names =
        listOf(
            stringResource(R.string.appearance_margins_narrow),
            stringResource(R.string.appearance_margins_normal),
            stringResource(R.string.appearance_margins_wide),
        )
    Choice(stringResource(R.string.appearance_margins), MARGIN_SCALES.zip(names), value, onSelect)
}

@Composable
private fun ColumnsSection(
    columns: Int?,
    fitting: Int?,
    onSelect: (Int?) -> Unit,
) {
    val options =
        listOf<Pair<Int?, String>>(null to stringResource(R.string.appearance_columns_auto)) +
            (1..MAX_COLUMNS).map { it to it.toString() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Choice(stringResource(R.string.appearance_columns), options, columns, onSelect)
        if (columns != null && fitting != null && columns > fitting) {
            BasicText(
                text = stringResource(R.string.appearance_columns_note, fitting),
                style = BifolioTheme.typography.caption.copy(color = BifolioTheme.palette.inkMuted),
            )
        }
    }
}

@Composable
private fun ModeSection(
    mode: ReadingMode,
    onSelect: (ReadingMode) -> Unit,
) {
    val options =
        listOf(
            ReadingMode.PAGED to stringResource(R.string.appearance_mode_pages),
            ReadingMode.SCROLL to stringResource(R.string.appearance_mode_scroll),
        )
    Choice(stringResource(R.string.appearance_mode), options, mode, onSelect)
}

@Composable
private fun TurnSection(
    turn: TurnChoice,
    onSelect: (TurnChoice) -> Unit,
) {
    val options =
        listOf(
            TurnChoice.SLIDE to stringResource(R.string.appearance_turn_slide),
            TurnChoice.FADE to stringResource(R.string.appearance_turn_fade),
            TurnChoice.NONE to stringResource(R.string.appearance_turn_none),
        )
    Choice(stringResource(R.string.appearance_turn), options, turn, onSelect)
}

private const val MAX_COLUMNS = 2
