package dev.dcesares.bifolio.layout

import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.ColumnAlignment

/**
 * One run of styled text broken into lines by the measurer. The reader builds the platform layout from the
 * same inputs and draws the lines the paginator counted, instead of breaking the text again (ADR-0013).
 * Identity matters: the same leaf object is returned for the same block and width, so it can key caches.
 */
public class TextLeaf(
    public val text: String,
    public val styles: TextStyles,
    public val role: TextRole,
    /** Width in px the text was broken for. */
    public val width: Int,
)

public enum class MarkerKind { BULLET, NUMBER, TASK_UNCHECKED, TASK_CHECKED }

public enum class BarKind { QUOTE, CALLOUT }

/**
 * Drawing instructions of one page column, in px from the top-left corner of the column (not the window).
 * Produced off the main thread by [PageDetailer]; nothing here needs measuring at draw time.
 */
public sealed interface DrawItem {
    public val y: Int
    public val height: Int
}

/** Lines `[clipTop, clipBottom)` of [leaf] (px inside the leaf), drawn with the leaf's top at `y - clipTop`. */
public class DrawText(
    public val leaf: TextLeaf,
    public val x: Int,
    override val y: Int,
    override val height: Int,
    public val clipTop: Int,
    public val clipBottom: Int,
) : DrawItem

public class DrawMarker(
    public val x: Int,
    public val width: Int,
    override val y: Int,
    override val height: Int,
    public val kind: MarkerKind,
    public val number: Int,
) : DrawItem

public class DrawBar(
    public val x: Int,
    override val y: Int,
    override val height: Int,
    public val kind: BarKind,
) : DrawItem

public class DrawCodeSurface(
    public val x: Int,
    public val width: Int,
    override val y: Int,
    override val height: Int,
) : DrawItem

public class DrawRule(
    public val x: Int,
    public val width: Int,
    override val y: Int,
    override val height: Int,
) : DrawItem

/** Image, display math or front matter card (PAG-07). A placeholder until M5 renders their content. */
public class DrawAtomic(
    public val block: Block,
    public val x: Int,
    public val width: Int,
    override val y: Int,
    override val height: Int,
) : DrawItem

public class DrawCalloutTitle(
    public val kind: CalloutKind,
    public val x: Int,
    override val y: Int,
    override val height: Int,
) : DrawItem

public class DrawCell(
    public val leaf: TextLeaf?,
    public val x: Int,
    public val width: Int,
    public val alignment: ColumnAlignment,
)

/**
 * A table row. [tableWidth] is the width of all columns; when it exceeds [columnWidth] the table is a framed
 * figure that scrolls sideways (PAG-06), keyed by [blockIndex]. Cell x and width are relative to [x]; a cell's
 * leaf is broken for the cell width minus the table cell padding on both sides.
 */
public class DrawTableRow(
    public val blockIndex: Int,
    public val header: Boolean,
    public val cells: List<DrawCell>,
    public val x: Int,
    public val tableWidth: Int,
    public val columnWidth: Int,
    override val y: Int,
    override val height: Int,
) : DrawItem

/** Quiet mark in the margin at the top of a code block or table continued from the previous page (PAG-04, PAG-06). */
public class DrawContinuation(
    public val x: Int,
    override val y: Int,
    override val height: Int,
) : DrawItem

/** Everything needed to paint one page column. */
public class PageDraw(
    public val items: List<DrawItem>,
    public val columnWidth: Int,
    /** Height in px used by the content; the end ornament sits below it (PAG-12). */
    public val usedHeight: Int,
    public val endsDocument: Boolean,
)
