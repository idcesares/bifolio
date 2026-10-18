package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The draw model keeps what the paginator counted (ADR-0013): same heights, nothing outside the column. */
class PageDetailerTest {
    private val mixed =
        markdown(
            "# Title",
            para(6),
            "- ${para(3, perLine = 18)}\n- ${para(2, perLine = 18)}",
            "> ${nested(4)}",
            "```\n" + List(30) { "code line $it" }.joinToString("\n") + "\n```",
            "| a | b |\n|---|---|\n" + List(25) { "| r$it | v$it |" }.joinToString("\n"),
            "---",
        )

    private fun detailAll(
        md: String,
        geometry: Geometry = testGeometry(lines = 12),
    ): List<PageDraw> {
        val (doc, flow) = paginate(md, geometry)
        val detailer = PageDetailer(doc, FakeBlockMeasurer(), geometry)
        return flow.pages.mapIndexed { i, page -> detailer.detail(page, i % geometry.columnCount) }
    }

    @Test
    fun `used height matches the paginator and every item stays inside the column`() {
        val geometry = testGeometry(lines = 12)
        val (doc, flow) = paginate(mixed, geometry)
        val detailer = PageDetailer(doc, FakeBlockMeasurer(), geometry)
        for (page in flow.pages) {
            val draw = detailer.detail(page, 0)
            assertEquals(page.usedHeight, draw.usedHeight)
            for (item in draw.items) {
                assertTrue(item.y >= 0 && item.y + item.height <= geometry.columnHeight, "$item outside the page")
            }
        }
    }

    @Test
    fun `markers bars and rules are emitted once for the lines they belong to`() {
        val draws = detailAll(mixed, testGeometry(lines = 40))
        val items = draws.flatMap { it.items }
        assertEquals(2, items.count { it is DrawMarker })
        assertEquals(1, items.count { it is DrawBar })
        assertEquals(1, items.count { it is DrawRule })
    }

    @Test
    fun `a continued code block shows the continuation mark and a continued table repeats its header`() {
        val draws = detailAll(mixed)
        val items = draws.flatMap { it.items }
        assertTrue(items.any { it is DrawContinuation }, "no continuation mark")
        val headers = items.count { it is DrawTableRow && it.header }
        val tableSlices = draws.count { d -> d.items.any { it is DrawTableRow } }
        assertEquals(tableSlices, headers, "each page with table rows starts with the header")
        for (draw in draws) {
            val rows = draw.items.filterIsInstance<DrawTableRow>()
            if (rows.isNotEmpty()) assertTrue(rows.first().header)
        }
    }

    @Test
    fun `text items together cover every rendered line of a paragraph`() {
        val geometry = testGeometry(lines = 6)
        val md = markdown(para(30))
        val (doc, flow) = paginate(md, geometry)
        val detailer = PageDetailer(doc, FakeBlockMeasurer(), geometry)
        val lines =
            flow.pages.sumOf { page ->
                val texts = detailer.detail(page, 0).items.filterIsInstance<DrawText>()
                texts.sumOf { (it.clipBottom - it.clipTop) / 20 }
            }
        assertEquals(30, lines)
    }

    @Test
    fun `the anchor line of a page anchor is a visible line at the top of its column (ANC-03)`() {
        val geometry = testGeometry(lines = 12)
        val (doc, flow) = paginate(mixed, geometry)
        val detailer = PageDetailer(doc, FakeBlockMeasurer(), geometry)
        for (page in flow.pages) {
            val line = detailer.anchorLine(page, 0, page.anchor)
            val header = page.slices.first().repeatedHeaderHeight
            assertEquals(header, line.y, "first line of the page sits under the repeated header, if any")
            assertTrue(line.height > 0 && line.y + line.height <= geometry.columnHeight, "outside the column")
        }
    }

    @Test
    fun `an anchor in the middle of a paragraph maps to a later line than the page start`() {
        val geometry = testGeometry(lines = 40)
        val (doc, flow) = paginate(markdown(para(12)), geometry)
        val detailer = PageDetailer(doc, FakeBlockMeasurer(), geometry)
        val page = flow.pages.first()
        val length = doc.blocks[0].range.length
        val start = detailer.anchorLine(page, 0, page.anchor)
        val middle = detailer.anchorLine(page, 0, Anchor(0, length / 2))
        assertTrue(middle.y > start.y, "mid-paragraph anchor must be below the first line")
        assertTrue(middle.y + middle.height <= geometry.columnHeight)
    }
}
