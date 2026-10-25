package dev.dcesares.bifolio.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.model.Block
import dev.dcesares.bifolio.model.BlockQuote
import dev.dcesares.bifolio.model.Callout
import dev.dcesares.bifolio.model.CalloutKind
import dev.dcesares.bifolio.model.CodeBlock
import dev.dcesares.bifolio.model.FootnoteDefinition
import dev.dcesares.bifolio.model.FrontMatter
import dev.dcesares.bifolio.model.Heading
import dev.dcesares.bifolio.model.HtmlDetails
import dev.dcesares.bifolio.model.ImageBlock
import dev.dcesares.bifolio.model.Inline
import dev.dcesares.bifolio.model.ListBlock
import dev.dcesares.bifolio.model.MathBlock
import dev.dcesares.bifolio.model.MermaidFence
import dev.dcesares.bifolio.model.Paragraph
import dev.dcesares.bifolio.model.Table
import dev.dcesares.bifolio.model.TaskState
import dev.dcesares.bifolio.model.ThematicBreak

/** Receives link destinations tapped inside text (NAV-07). The reader resolves them: headings and notes jump. */
internal val LocalLinkHandler = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Receives a footnote marker tapped inside text, with its bounds in window px (UI-09). */
internal val LocalNoteHandler = staticCompositionLocalOf<(NoteTap) -> Unit> { {} }

private const val MARKER_WIDTH_EM = 1.8f
private const val TASK_BOX_EM = 0.8f
private const val CHECK_START_X = 0.22f
private const val CHECK_MID_X = 0.43f
private const val CHECK_END_X = 0.78f
private const val CHECK_START_Y = 0.52f
private const val CHECK_MID_Y = 0.72f
private const val CHECK_END_Y = 0.3f

/** Gap between blocks: 0.6 lines of body text (DESIGN.md section 3). Scales with the font size. */
@Composable
internal fun paragraphGap(): Dp {
    val type = BifolioTheme.typography
    return with(LocalDensity.current) { (type.bodySize * type.paragraphGapEm).toDp() }
}

@Composable
internal fun inlineText(inlines: List<Inline>): AnnotatedString {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val onLink = LocalLinkHandler.current
    val theme =
        remember(palette, type) {
            InlineTheme(palette.link, palette.codeSurface, palette.accent, type.code.fontFamily ?: FontFamily.Monospace)
        }
    return remember(inlines, theme, onLink) { buildInlineText(inlines, theme, onLink) }
}

/** One block of the document. Atomic types render whole; containers recurse. */
@Composable
internal fun BlockContent(
    block: Block,
    modifier: Modifier = Modifier,
) {
    val type = BifolioTheme.typography
    when (block) {
        is Heading ->
            HighlightedText(
                text = inlineText(block.content),
                block = block,
                style = type.heading(block.level),
                modifier = modifier.semantics { heading() },
                inlines = block.content,
            )
        is Paragraph ->
            HighlightedText(inlineText(block.content), block, type.body, modifier, inlines = block.content)
        is ListBlock -> ListView(block, modifier)
        is BlockQuote -> BarredBlocks(block.blocks, BifolioTheme.palette.rule, null, modifier)
        is CodeBlock -> CodeView(block.code, modifier, block)
        is Table -> TableView(block, modifier)
        is ThematicBreak ->
            Box(modifier.fillMaxWidth().height(Spacing.Rule).background(BifolioTheme.palette.rule))
        is HtmlDetails -> DetailsView(block, modifier)
        is Callout -> BarredBlocks(block.blocks, BifolioTheme.palette.accent, calloutLabel(block.kind), modifier)
        is FootnoteDefinition -> BlockStack(block.blocks, modifier)
        is ImageBlock -> ImageView(block, modifier)
        is MathBlock -> MathView(block, modifier)
        is FrontMatter -> FrontMatterView(block, modifier)
        is MermaidFence ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                BasicText(stringResource(R.string.reader_diagram_not_rendered), style = type.caption)
                CodeView(block.source, block = block)
            }
    }
}

@Composable
internal fun calloutLabel(kind: CalloutKind): String =
    stringResource(
        when (kind) {
            CalloutKind.NOTE -> R.string.reader_callout_note
            CalloutKind.TIP -> R.string.reader_callout_tip
            CalloutKind.IMPORTANT -> R.string.reader_callout_important
            CalloutKind.WARNING -> R.string.reader_callout_warning
            CalloutKind.CAUTION -> R.string.reader_callout_caution
        },
    )

@Composable
private fun BlockStack(
    blocks: List<Block>,
    modifier: Modifier = Modifier,
    gap: Dp = paragraphGap(),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        blocks.forEach { BlockContent(it) }
    }
}

@Composable
private fun BarredBlocks(
    blocks: List<Block>,
    barColor: Color,
    label: String?,
    modifier: Modifier,
) {
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(Spacing.Xs)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Spacing.Xs))
                .background(barColor),
        )
        Spacer(Modifier.width(Spacing.L))
        Column(verticalArrangement = Arrangement.spacedBy(paragraphGap())) {
            if (label != null) {
                BasicText(label, style = BifolioTheme.typography.body.copy(fontWeight = FontWeight.Bold))
            }
            blocks.forEach { BlockContent(it) }
        }
    }
}

@Composable
internal fun CodeView(
    code: String,
    modifier: Modifier = Modifier,
    /** The block whose search hits are painted in the code, or null for blocks the search does not read. */
    block: Block? = null,
) {
    val language = (block as? CodeBlock)?.takeIf { it.fenced }?.language
    val tokens = BifolioTheme.palette.codeTokens
    val spans = rememberTokenSpans(code, language)
    val text = remember(code, spans, tokens) { spans?.let { codeAnnotated(code, it, tokens) } ?: AnnotatedString(code) }
    val announced = language?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.reader_code_language, it) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.CodeRadius))
            .background(BifolioTheme.palette.codeSurface)
            .semantics { if (announced != null) stateDescription = announced },
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(Spacing.M)) {
            HighlightedText(text, block, BifolioTheme.typography.code, softWrap = false)
        }
    }
}

@Composable
private fun ListView(
    block: ListBlock,
    modifier: Modifier,
) {
    val type = BifolioTheme.typography
    val gap = if (block.tight) Spacing.Xs else paragraphGap()
    val density = LocalDensity.current
    val markerWidth = with(density) { (type.bodySize * MARKER_WIDTH_EM).toDp() }
    val lineHeight = with(density) { type.body.lineHeight.toDp() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        block.items.forEachIndexed { position, item ->
            Row {
                Box(
                    Modifier.width(markerWidth).padding(end = Spacing.S),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    when {
                        item.task != null ->
                            Box(Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
                                TaskBox(item.task == TaskState.CHECKED)
                            }
                        block.ordered ->
                            BasicText(
                                "${block.startNumber + position}.",
                                style = type.body.copy(textAlign = TextAlign.End),
                            )
                        else -> BasicText("•", style = type.body.copy(textAlign = TextAlign.End))
                    }
                }
                BlockStack(item.blocks, Modifier.weight(1f), gap)
            }
        }
    }
}

/** A read-only task box drawn as vectors, so it does not depend on font coverage. Never a toggle (SYN-03). */
@Composable
private fun TaskBox(checked: Boolean) {
    val ink = BifolioTheme.palette.ink
    val accent = BifolioTheme.palette.accent
    val side = with(LocalDensity.current) { (BifolioTheme.typography.bodySize * TASK_BOX_EM).toDp() }
    val description = stringResource(if (checked) R.string.reader_task_checked else R.string.reader_task_unchecked)
    Canvas(Modifier.size(side).semantics { contentDescription = description }) {
        val stroke = Spacing.Hairline.toPx()
        val inset = stroke / 2
        val box = Size(size.width - stroke, size.height - stroke)
        drawRoundRect(
            color = if (checked) accent else ink,
            topLeft = Offset(inset, inset),
            size = box,
            cornerRadius = CornerRadius(stroke * 2),
            style = Stroke(width = stroke),
        )
        if (checked) {
            val w = size.width
            val h = size.height
            val start = Offset(w * CHECK_START_X, h * CHECK_START_Y)
            val mid = Offset(w * CHECK_MID_X, h * CHECK_MID_Y)
            val end = Offset(w * CHECK_END_X, h * CHECK_END_Y)
            drawLine(accent, start, mid, stroke, StrokeCap.Round)
            drawLine(accent, mid, end, stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun DetailsView(
    block: HtmlDetails,
    modifier: Modifier,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    var open by rememberSaveable(block.range.start) { mutableStateOf(block.open) }
    val label = stringResource(if (open) R.string.reader_details_collapse else R.string.reader_details_expand)
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Spacing.TouchTarget)
                .clickable(role = Role.Button, onClickLabel = label) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BifolioIcon(if (open) BifolioIcons.ChevronDown else BifolioIcons.ChevronRight, null, palette.inkMuted)
            Spacer(Modifier.width(Spacing.S))
            HighlightedText(
                inlineText(block.summary),
                block,
                type.body.copy(fontWeight = FontWeight.SemiBold),
                inlines = block.summary,
            )
        }
        if (open) {
            BlockStack(block.blocks, Modifier.padding(start = Spacing.Xl, top = Spacing.S))
        }
    }
}
