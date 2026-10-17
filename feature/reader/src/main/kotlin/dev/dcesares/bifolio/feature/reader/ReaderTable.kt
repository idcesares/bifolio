package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.model.ColumnAlignment
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.TableRow
import dev.dcesares.bifolio.model.plainText

private const val TABLE_TEXT_SCALE = 0.9f
private const val TABLE_CHAR_WIDTH_EM = 0.66f
private const val TABLE_MIN_CHARS = 5
private const val TABLE_MAX_CHARS = 28

/** GFM table in scroll mode: columns sized from their longest cell, horizontal scroll when wider (SYN-06). */
@Composable
internal fun TableView(
    table: Table,
    modifier: Modifier,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val density = LocalDensity.current
    val cellStyle =
        type.body.copy(
            fontSize = type.bodySize * TABLE_TEXT_SCALE,
            lineHeight = type.body.lineHeight * TABLE_TEXT_SCALE,
        )
    val widths =
        remember(table, type.bodySize, density) {
            val perChar = with(density) { (type.bodySize * TABLE_CHAR_WIDTH_EM * TABLE_TEXT_SCALE).toDp() }
            List(table.columnCount) { column ->
                val longest =
                    (listOf(table.header) + table.rows)
                        .maxOf { row -> row.cells.getOrNull(column)?.let { plainText(it.content).length } ?: 0 }
                perChar * longest.coerceIn(TABLE_MIN_CHARS, TABLE_MAX_CHARS) + Spacing.M * 2
            }
        }
    val totalWidth = widths.fold(0.dp) { a, b -> a + b }
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            TableRowView(table.header, table.alignments, widths, cellStyle, header = true)
            Box(Modifier.width(totalWidth).height(Spacing.Rule).background(palette.ink))
            table.rows.forEach { row ->
                TableRowView(row, table.alignments, widths, cellStyle, header = false)
                Box(Modifier.width(totalWidth).height(Spacing.Rule).background(palette.rule))
            }
        }
    }
}

@Composable
private fun TableRowView(
    row: TableRow,
    alignments: List<ColumnAlignment>,
    widths: List<Dp>,
    style: TextStyle,
    header: Boolean,
) {
    val background = if (header) BifolioTheme.palette.codeSurface else Color.Transparent
    Row(Modifier.height(IntrinsicSize.Min).background(background)) {
        widths.forEachIndexed { column, width ->
            val align =
                when (alignments[column]) {
                    ColumnAlignment.NONE -> TextAlign.Start
                    ColumnAlignment.LEFT -> TextAlign.Left
                    ColumnAlignment.CENTER -> TextAlign.Center
                    ColumnAlignment.RIGHT -> TextAlign.Right
                }
            val cell = row.cells.getOrNull(column)
            Box(Modifier.width(width).fillMaxHeight().padding(horizontal = Spacing.M, vertical = Spacing.S)) {
                if (cell != null) {
                    BasicText(
                        inlineText(cell.content),
                        style = style.copy(textAlign = align, fontWeight = if (header) FontWeight.SemiBold else null),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
