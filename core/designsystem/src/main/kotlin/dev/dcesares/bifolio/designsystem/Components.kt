package dev.dcesares.bifolio.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ButtonRadius = 20.dp

/** Outlined action in the accent color, at least 48dp tall. */
@Composable
public fun BifolioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val palette = BifolioTheme.palette
    val shape = RoundedCornerShape(ButtonRadius)
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = Spacing.TouchTarget)
                .clip(shape)
                .border(BorderStroke(1.5.dp, palette.accent), shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.L, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        if (icon != null) BifolioIcon(icon, contentDescription = null, tint = palette.accent)
        BasicText(
            text = text,
            style = BifolioTheme.typography.body.copy(color = palette.accent, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Icon-only action with a 48dp touch target. [description] is the accessibility label. */
@Composable
public fun BifolioIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
                .clip(RoundedCornerShape(ButtonRadius))
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BifolioIcon(icon, contentDescription = description, tint = BifolioTheme.palette.inkMuted)
    }
}
