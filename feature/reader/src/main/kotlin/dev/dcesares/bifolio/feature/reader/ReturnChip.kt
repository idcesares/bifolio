package dev.dcesares.bifolio.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing

private val ChipRadius = 20.dp

/**
 * The return chip (ANC-06): offered after a jump, it takes the reader back to where the jump started. [visible]
 * is driven by the view model, so a fold or rotation never drops it. Fades like the chrome (`motion.fade`).
 */
@Composable
internal fun ReturnChip(
    visible: Boolean,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BifolioTheme.palette
    val shape = RoundedCornerShape(ChipRadius)
    val insets = ChromeInsets.asPaddingValues()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.padding(bottom = insets.calculateBottomPadding() + Spacing.L),
        enter = fadeIn(BifolioMotion.fade()),
        exit = fadeOut(BifolioMotion.fade()),
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = Spacing.TouchTarget)
                    .clip(shape)
                    .background(palette.page)
                    .border(1.5.dp, palette.accent, shape)
                    .clickable(role = Role.Button, onClick = onReturn)
                    .padding(horizontal = Spacing.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BifolioIcon(BifolioIcons.Undo2, null, palette.accent)
            BasicText(
                text = stringResource(R.string.reader_return),
                modifier = Modifier.padding(start = Spacing.S),
                style = BifolioTheme.typography.caption.copy(color = palette.ink),
            )
        }
    }
}
