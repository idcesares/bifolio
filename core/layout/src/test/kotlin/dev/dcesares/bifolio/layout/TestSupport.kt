package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Anchor
import dev.dcesares.bifolio.model.Document
import dev.dcesares.bifolio.parse.MarkdownParser

/** Test metrics: with [FakeBlockMeasurer] defaults one text line is 20 px and a block gap is exactly one line. */
internal val TEST_METRICS =
    FlowMetrics(
        blockGap = 20,
        innerGap = 0,
        listIndent = 20,
        quoteIndent = 20,
        ruleHeight = 20,
        tableCellPadding = 0,
        tableMinColumn = 40,
        atomicMaxHeightPercent = 85,
    )

/** A geometry with [columns] equal columns of [lines] text lines of 20 chars, built directly (no window). */
internal fun testGeometry(
    lines: Int = 10,
    columns: Int = 1,
    columnWidth: Int = 200,
    metrics: FlowMetrics = TEST_METRICS,
    typographyKey: Int = 0,
): Geometry {
    val gap = 40
    return Geometry(
        windowWidth = columns * columnWidth + (columns - 1) * gap + 100,
        windowHeight = lines * 20 + 100,
        minMeasure = columnWidth,
        targetMeasure = columnWidth,
        maxMeasure = columnWidth,
        floorMeasure = columnWidth,
        baseGap = gap,
        outerMargin = 50,
        leftMargin = 50,
        rightMargin = 50,
        verticalMargin = 50,
        columnCount = columns,
        columnLefts = List(columns) { 50 + it * (columnWidth + gap) },
        columnWidths = List(columns) { columnWidth },
        columnHeight = lines * 20,
        metrics = metrics,
        typographyKey = typographyKey,
    )
}

/** Text of exactly [n] lines at [perLine] characters per line with [FakeBlockMeasurer] defaults. */
internal fun para(
    n: Int,
    perLine: Int = 20,
    letter: Char = 'a',
): String = List(n) { letter.toString().repeat(perLine - 1) }.joinToString(" ")

/** Text for a list item or quote paragraph: 10 px indent less gives 18 characters per line. */
internal fun nested(n: Int): String = para(n, perLine = 18)

internal fun markdown(vararg blocks: String): String = blocks.joinToString("\n\n") + "\n"

internal fun paginate(
    md: String,
    geometry: Geometry = testGeometry(),
    measurer: BlockMeasurer = FakeBlockMeasurer(),
): Pair<Document, PageFlow> {
    val doc = MarkdownParser.parse(md)
    return doc to Paginator(doc, measurer, geometry).paginateAll()!!
}

/** Slices as `block:start-end` for compact assertions. */
internal fun Page.shape(): String = slices.joinToString(" ") { "${it.blockIndex}:${it.startLine}-${it.endLine}" }

internal fun PageFlow.shape(): List<String> = pages.map { it.shape() }

internal fun anchorOf(
    block: Int,
    offset: Int = 0,
) = Anchor(block, offset)
