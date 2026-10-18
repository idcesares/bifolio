package dev.dcesares.bifolio.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** LAY-12: columns of unequal width on each side of a fold. */
class MixedWidthColumnsTest {
    // At 10 px per character: 20 chars per line in the 200 px column, 30 in the 300 px one.
    private val geometry = testGeometry(lines = 10, widths = listOf(200, 300))

    private fun words(
        n: Int,
        letter: Char,
    ) = para(n, perLine = 10, letter = letter)

    @Test
    fun `a block that fits its column keeps that column's width, a block that crosses columns uses the narrowest`() {
        val (doc, flow) =
            paginate(markdown(words(20, 'a'), words(21, 'b'), words(60, 'c')), geometry)
        assertEquals(listOf("0:0-10", "1:0-7 2:0-2", "2:2-12", "2:12-22", "2:22-30"), flow.shape())

        val detailer = PageDetailer(doc, FakeBlockMeasurer(), geometry)
        val widths =
            flow.pages.withIndex().map { (i, page) ->
                detailer
                    .detail(page, i % 2)
                    .items
                    .filterIsInstance<DrawText>()
                    .map { it.leaf.width }
            }
        // Page 1 sits in the 300 px column: block 1 fits whole and is broken for 300, block 2 starts there and
        // goes on to the next columns, so all of its slices are broken for the narrowest column, 200.
        assertEquals(listOf(listOf(200), listOf(300, 200), listOf(200), listOf(200), listOf(200)), widths)
    }

    @Test
    fun `a block continues exactly where the previous column stopped even when the next column is wider`() {
        val (doc, flow) = paginate(markdown(words(60, 'a')), testGeometry(lines = 10, widths = listOf(200, 300)))
        val layout = BlockFlattener(doc, FakeBlockMeasurer(), geometry).layout(0, 200)
        assertEquals(listOf("0:0-10", "0:10-20", "0:20-30"), flow.shape())
        assertEquals(30, layout.lineCount)
        assertEquals(60 * 9 + 59, layout.chars.sum(), "every character once")
    }
}
