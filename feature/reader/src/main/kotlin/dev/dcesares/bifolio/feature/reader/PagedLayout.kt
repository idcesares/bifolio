package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.layout.BlockMeasurer
import dev.dcesares.bifolio.layout.FlowMetrics
import dev.dcesares.bifolio.layout.FoldSpec
import dev.dcesares.bifolio.layout.Geometry
import dev.dcesares.bifolio.layout.PageDetailer
import dev.dcesares.bifolio.layout.PageFlow
import dev.dcesares.bifolio.layout.WindowSpec
import dev.dcesares.bifolio.text.FontSet
import dev.dcesares.bifolio.text.StaticLayoutBlockMeasurer
import dev.dcesares.bifolio.text.SystemFontSet
import dev.dcesares.bifolio.text.TextStyleConfig
import java.util.Locale

/**
 * What the window asks of the paginator: its usable size in px and the text settings that change glyph
 * metrics. Geometry comes from here and nowhere else (LAY-01). Equal requests are the same layout.
 */
public data class PageRequest(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val fontScale: Float,
    /** Body size in sp, taken from the theme typography. */
    val bodySp: Float,
    val languageTag: String,
    /**
     * Folding feature in the coordinates of the usable area (insets already subtracted), or null. Part of the
     * request so a hinge that moves without a window resize is a different layout (LAY-01, PAG-10).
     */
    val fold: FoldSpec? = null,
    /** The user's typography and layout choices (UI-10). Part of the request, so a change repaginates (PAG-10). */
    val lineHeight: Float = 1.5f,
    val marginScale: Float = 1f,
    val forcedColumns: Int? = null,
    val hyphenate: Boolean = true,
    val justify: Boolean = false,
    /** Body and code faces. Equal font sets measure alike, so a change repaginates (PAG-10). */
    val fonts: FontSet = SystemFontSet,
) {
    /** Body size in px at this density and font scale (TYP-01). */
    public val bodyPx: Float get() = bodySp * density * fontScale
}

/** Everything the platform contributes to a pagination: the measurer, the window spec and the text config. */
public class PreparedText(
    public val measurer: BlockMeasurer,
    public val spec: WindowSpec,
    public val config: TextStyleConfig,
)

/** Turns a [PageRequest] into measuring and geometry inputs. The Android implementation uses `StaticLayout`. */
public fun interface TextEngine {
    public fun prepare(request: PageRequest): PreparedText
}

/**
 * Hyphenation locale (TYP-05): the document's front matter `lang` when it is a valid BCP 47 tag, else [appTag].
 * An underscore form such as `pt_BR` is read as `pt-BR`.
 */
internal fun hyphenationTag(
    documentLanguage: String?,
    appTag: String,
): String {
    val tag = documentLanguage?.trim()?.replace('_', '-').orEmpty()
    val locale = Locale.forLanguageTag(tag)
    return if (tag.isEmpty() || locale.language.isEmpty()) appTag else locale.toLanguageTag()
}

/** Spacing in the flow is a multiple of the body size, so it follows the user's text scale. */
internal fun flowMetricsFor(request: PageRequest): FlowMetrics {
    val body = request.bodyPx
    val dp = request.density
    return FlowMetrics(
        blockGap = (body * BLOCK_GAP_EM).toInt(),
        innerGap = (body * INNER_GAP_EM).toInt(),
        listIndent = (body * LIST_INDENT_EM).toInt(),
        quoteIndent = (QUOTE_INDENT_DP * dp).toInt(),
        ruleHeight = (body * RULE_EM).toInt(),
        tableCellPadding = (CELL_PADDING_DP * dp).toInt(),
        tableMinColumn = (TABLE_MIN_COLUMN_DP * dp).toInt(),
        codePadding = (CODE_PADDING_DP * dp).toInt(),
    )
}

private const val BLOCK_GAP_EM = 0.9f
private const val INNER_GAP_EM = 0.5f
private const val LIST_INDENT_EM = 1.4f
private const val RULE_EM = 1.5f
private const val QUOTE_INDENT_DP = 16f
private const val CELL_PADDING_DP = 8f
private const val TABLE_MIN_COLUMN_DP = 48f
private const val CODE_PADDING_DP = 6f

/** Production [TextEngine]: `StaticLayout` measuring with the body serif face (ADR-0013). */
public class AndroidTextEngine : TextEngine {
    override fun prepare(request: PageRequest): PreparedText {
        val config =
            TextStyleConfig(
                bodySizePx = request.bodyPx,
                lineHeight = request.lineHeight,
                locale = Locale.forLanguageTag(request.languageTag),
                hyphenate = request.hyphenate,
                justify = request.justify,
                fonts = request.fonts,
            ).withMeasuredCharWidth()
        val spec =
            WindowSpec(
                widthPx = request.widthPx,
                heightPx = request.heightPx,
                density = request.density,
                charWidthPx = config.charWidthPx,
                marginScale = request.marginScale,
                forcedColumns = request.forcedColumns,
                metrics = flowMetricsFor(request),
                typographyKey = config.hashCode(),
                fold = request.fold,
                // POS-04: the tabletop minimum region is four body lines of the real line height.
                bodyLineHeightPx = config.bodySizePx * config.lineHeight,
            )
        return PreparedText(StaticLayoutBlockMeasurer(config), spec, config)
    }
}

/** How far a pagination has got (PAG-09). */
public enum class PaginationStage { PROVISIONAL, CANONICAL, COMPLETE }

/** Milliseconds since the request, for PRF-04. A null stage was not reached. */
public data class PaginationTimings(
    val provisionalMillis: Long? = null,
    val canonicalMillis: Long? = null,
    val completeMillis: Long? = null,
)

/**
 * One pagination snapshot with the objects the surface needs to draw it. Replaced as the pagination
 * advances; the surface reads the visible spread from the reading anchor on each one (ANC-03).
 */
public class PagedLayout(
    public val request: PageRequest,
    public val geometry: Geometry,
    public val config: TextStyleConfig,
    public val flow: PageFlow,
    public val detailer: PageDetailer,
    /** Which snapshot of image sizes this layout was made from; a new one means different boxes (PAG-07). */
    public val imageKey: Int = 0,
    /** Which snapshot of formula sizes this layout was made from (SYN-12, PAG-07). */
    public val mathKey: Int = 0,
) {
    public val stage: PaginationStage
        get() =
            when {
                flow.complete -> PaginationStage.COMPLETE
                flow.provisional -> PaginationStage.PROVISIONAL
                else -> PaginationStage.CANONICAL
            }
}
