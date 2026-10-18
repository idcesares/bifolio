package dev.dcesares.bifolio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing

private val ChipRadius = 20.dp
private val ToggleMinWidth = 64.dp

@Composable
internal fun Label(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = BifolioTheme.typography.caption.copy(color = BifolioTheme.palette.inkMuted),
    )
}

/** A labelled row of exclusive options. Each option is a chip at least 48dp tall; the selected one is filled. */
@Composable
internal fun <T> Choice(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Label(label)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            for ((value, name) in options) {
                Chip(name, value == selected, Modifier.weight(1f)) { onSelect(value) }
            }
        }
    }
}

@Composable
internal fun Chip(
    text: String,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val shape = RoundedCornerShape(ChipRadius)
    Box(
        modifier
            .defaultMinSize(minHeight = Spacing.TouchTarget)
            .clip(shape)
            .background(if (isSelected) palette.accent else palette.page)
            .border(Spacing.Rule, if (isSelected) palette.accent else palette.rule, shape)
            .semantics { selected = isSelected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.S),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style =
                BifolioTheme.typography.caption.copy(
                    color = if (isSelected) palette.page else palette.ink,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
    }
}

@Composable
internal fun StepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = BifolioTheme.palette
    Box(
        Modifier
            .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
            .clip(RoundedCornerShape(ChipRadius))
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = glyph,
            style =
                BifolioTheme.typography.body.copy(
                    color = if (enabled) palette.accent else palette.inkMuted,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Spacing.TouchTarget)
            .semantics(mergeDescendants = true) { selected = checked }
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = BifolioTheme.typography.caption.copy(color = BifolioTheme.palette.ink),
        )
        Chip(
            text = stringResource(if (checked) R.string.appearance_on else R.string.appearance_off),
            isSelected = checked,
            modifier = Modifier.defaultMinSize(minWidth = ToggleMinWidth),
        ) { onChange(!checked) }
    }
}
