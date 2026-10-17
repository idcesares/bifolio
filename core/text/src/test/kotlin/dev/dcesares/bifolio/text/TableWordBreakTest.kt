package dev.dcesares.bifolio.text

import dev.dcesares.bifolio.layout.DrawTableRow
import dev.dcesares.bifolio.layout.FlowMetrics
import dev.dcesares.bifolio.layout.GeometryCalculator
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.Paginator
import dev.dcesares.bifolio.layout.TextLeaf
import dev.dcesares.bifolio.layout.WindowSpec
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
        return rows
            .flatMap { it.cells }
            .mapNotNull { it.leaf }
            .flatMap { leaf -> midWordBreaks(measurer, leaf) }
    }

    @Test
    fun `no table cell breaks inside a word at 100 percent`() = assertEquals(emptyList<String>(), brokenWords(1f))

    @Test
    fun `no table cell breaks inside a word at 200 percent`() = assertEquals(emptyList<String>(), brokenWords(2f))

    private fun midWordBreaks(
        measurer: StaticLayoutBlockMeasurer,
        leaf: TextLeaf,
    ): List<String> {
        val m = measurer.measureLines(leaf.text, leaf.role, leaf.width, leaf.styles)
        return (0 until m.count - 1)
            .filter { !leaf.text[m.ends[it] - 1].isWhitespace() }
            .map { "'${leaf.text}' breaks inside a word in line $it" }
    }
}
