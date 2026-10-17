package dev.dcesares.bifolio.text

import dev.dcesares.bifolio.layout.DrawTableRow
import dev.dcesares.bifolio.layout.FlowMetrics
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.Paginator
import dev.dcesares.bifolio.layout.WindowSpec
import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** PAG-06 with the real measurer: a squeezed table cell never breaks inside a word, even at 200% text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TableWordBreakTest {
    private val markdown =
        """
        | Left | Center | Right |
        | :--- | :----: | ----: |
        | a | The quick brown fox jumps over the lazy dog and keeps running through the quiet forest | z |
        | Identifier | Notes about the thing that was named before | 12 |

        """.trimIndent()

    private fun brokenWords(scale: Float): List<String> {
        val config = TextStyleConfig(bodySizePx = 32f * scale).withMeasuredCharWidth()
        val measurer = StaticLayoutBlockMeasurer(config)
        val spec =
            WindowSpec(
                widthPx = 1080,
                heightPx = 2400,
                density = 2.625f,
                charWidthPx = config.charWidthPx,
                metrics = FlowMetrics(tableCellPadding = 21, tableMinColumn = 126),
                typographyKey = config.hashCode(),
            )
        val geometry = GeometryCalculator.compute(spec)
        val doc = MarkdownParser.parse(markdown)
        val flow = Paginator(doc, measurer, geometry).paginateAll()!!
        val draw = PageDetailer(doc, measurer, geometry).detail(flow.pages.first(), 0)
        val rows = draw.items.filterIsInstance<DrawTableRow>()
        assertTrue("the table is drawn", rows.isNotEmpty())
        val broken = ArrayList<String>()
        for (row in rows) {
            for (cell in row.cells) {
                val leaf = cell.leaf ?: continue
                val m = measurer.measureLines(leaf.text, leaf.role, leaf.width, leaf.styles)
                for (i in 0 until m.count - 1) {
                    val last = leaf.text[m.ends[i] - 1]
                    if (!last.isWhitespace()) broken.add("'${leaf.text}' breaks after '${last}' in line $i")
                }
            }
        }
        return broken
    }

    @Test
    fun `no table cell breaks inside a word at 100 percent`() = assertEquals(emptyList<String>(), brokenWords(1f))

    @Test
    fun `no table cell breaks inside a word at 200 percent`() = assertEquals(emptyList<String>(), brokenWords(2f))
}
