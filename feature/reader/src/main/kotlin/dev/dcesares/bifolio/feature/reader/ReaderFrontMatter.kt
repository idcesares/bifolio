package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.layout.FrontMatterCard
import dev.dcesares.bifolio.model.FrontMatter

private const val TITLE_LEVEL = 4

/** The metadata card (SYN-13) on the scroll surface: the same title and rows the paged surface paints. */
@Composable
internal fun FrontMatterView(
    block: FrontMatter,
    modifier: Modifier = Modifier,
) {
    val type = BifolioTheme.typography
    val title = FrontMatterCard.title(block)
    val rows = FrontMatterCard.rows(block)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.CodeRadius))
            .background(BifolioTheme.palette.codeSurface)
            .padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        if (title != null) {
            BasicText(title, style = type.heading(TITLE_LEVEL), modifier = Modifier.semantics { heading() })
        }
        if (rows.isNotEmpty()) BasicText(rows, style = type.body)
    }
}
