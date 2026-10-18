package dev.dcesares.bifolio.text

import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import dev.dcesares.bifolio.layout.StyleFlag
import dev.dcesares.bifolio.layout.TextRole
import dev.dcesares.bifolio.layout.TextStyles

/**
 * Colors the reader applies when it draws. They never change glyph metrics, so the measurer builds the same
 * layouts without them (ADR-0013).
 */
public class TextPalette(
    public val ink: Int,
    public val inkMuted: Int,
    public val link: Int,
    public val accent: Int,
    public val mark: Int,
    public val codeSurface: Int,
)

/**
 * The one place that turns text, a [TextRole] and [TextStyles] into a `StaticLayout`. The measurer calls it to
 * count lines; the reader calls it with the same arguments (plus a [TextPalette]) to draw those lines. Same
 * inputs, same line breaks (ADR-0013). Holds no mutable state; any thread may call it.
 */
@Suppress("TooManyFunctions") // Builders for one layout recipe; splitting would scatter the shared inputs.
public class TextLayoutFactory(
    private val config: TextStyleConfig,
) {
    public fun layout(
        text: String,
        role: TextRole,
        width: Int,
        styles: TextStyles = TextStyles.NONE,
        palette: TextPalette? = null,
    ): StaticLayout {
        val w = maxOf(1, width)
        val paint = paintOf(role, palette)
        val body = role == TextRole.BODY
        val size = paint.textSize
        val fm = paint.fontMetrics
        val extra = lineHeightOf(role) * size - (fm.descent - fm.ascent)
        return StaticLayout.Builder
            .obtain(spanned(text, styles, palette), 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .setBreakStrategy(strategyOf(role, balanced(config, role, w)))
            .setHyphenationFrequency(hyphenation(body))
            .setJustificationMode(justification(body, w))
            .build()
    }

    private fun hyphenation(body: Boolean): Int =
        if (body && config.hyphenate) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE

    private fun justification(
        body: Boolean,
        width: Int,
    ): Int =
        if (body &&
            justifies(config, width)
        ) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }

    /** Width in px of the longest unbroken line of [text] for [role], with [styles] applied. */
    public fun intrinsicWidth(
        text: String,
        role: TextRole,
        styles: TextStyles,
    ): Int {
        if (text.isEmpty()) return 0
        var widest = 0f
        if (styles.count == 0) {
            val paint = paintOf(role, null)
            var from = 0
            while (from <= text.length) {
                var to = text.indexOf('\n', from)
                if (to < 0) to = text.length
                if (to > from) widest = maxOf(widest, paint.measureText(text, from, to))
                from = to + 1
            }
        } else {
            val l = layout(text, role, UNBOUNDED, styles)
            for (i in 0 until l.lineCount) widest = maxOf(widest, l.getLineWidth(i))
        }
        return Math.ceil(widest.toDouble()).toInt()
    }

    private fun spanned(
        text: String,
        styles: TextStyles,
        palette: TextPalette?,
    ): CharSequence {
        if (styles.count == 0) return text
        val s = SpannableString(text)
        for (i in 0 until styles.count) {
            val from = styles.starts[i].coerceIn(0, text.length)
            val to = styles.ends[i].coerceIn(from, text.length)
            if (to > from) apply(s, from, to, styles.flags[i], palette)
        }
        return s
    }

    private fun apply(
        s: Spannable,
        from: Int,
        to: Int,
        flags: Int,
        palette: TextPalette?,
    ) {
        fun put(span: Any) = s.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val bold = flags and StyleFlag.BOLD != 0
        val italic = flags and StyleFlag.ITALIC != 0
        if (bold || italic) put(FontSetSpan(config.fonts.body(if (bold) BOLD_WEIGHT else REGULAR_WEIGHT, italic)))
        if (flags and StyleFlag.STRIKETHROUGH != 0) put(StrikethroughSpan())
        if (flags and StyleFlag.UNDERLINE != 0 || flags and StyleFlag.LINK != 0) put(UnderlineSpan())
        applyOther(s, from, to, flags, palette)
    }

    private fun applyOther(
        s: Spannable,
        from: Int,
        to: Int,
        flags: Int,
        palette: TextPalette?,
    ) {
        fun put(span: Any) = s.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (flags and StyleFlag.CODE != 0) {
            put(FontSetSpan(config.fonts.code()))
            put(RelativeSizeSpan(INLINE_CODE_SCALE))
            if (palette != null) put(BackgroundColorSpan(palette.codeSurface))
        }
        if (flags and StyleFlag.SUPERSCRIPT != 0) {
            put(SuperscriptSpan())
            put(RelativeSizeSpan(SCRIPT_SCALE))
        }
        if (flags and StyleFlag.SUBSCRIPT != 0) {
            put(SubscriptSpan())
            put(RelativeSizeSpan(SCRIPT_SCALE))
        }
        if (palette != null) {
            if (flags and StyleFlag.LINK != 0) put(ForegroundColorSpan(palette.link))
            if (flags and StyleFlag.MARK != 0) put(BackgroundColorSpan(palette.mark))
        }
    }

    private fun lineHeightOf(role: TextRole): Float =
        when (role) {
            TextRole.HEADING_1 -> LH_H1
            TextRole.HEADING_2 -> LH_H2
            TextRole.HEADING_3 -> LH_H3
            TextRole.HEADING_4 -> LH_H4
            TextRole.HEADING_5, TextRole.HEADING_6 -> LH_H5
            TextRole.CODE -> LH_CODE
            TextRole.BODY, TextRole.TABLE_CELL, TextRole.TABLE_HEADER -> config.lineHeight
        }

    private fun strategyOf(
        role: TextRole,
        balanced: Boolean,
    ): Int =
        when {
            role == TextRole.CODE -> Layout.BREAK_STRATEGY_SIMPLE
            balanced -> Layout.BREAK_STRATEGY_BALANCED
            else -> Layout.BREAK_STRATEGY_HIGH_QUALITY
        }

    /** A fresh paint per call: `TextPaint` is not thread-safe and layouts keep a reference to it. */
    public fun paintOf(
        role: TextRole,
        palette: TextPalette? = null,
    ): TextPaint {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        paint.textSize = config.bodySizePx * scaleOf(role)
        paint.textLocale = config.locale
        paint.typeface =
            when {
                role == TextRole.CODE -> config.fonts.code()
                role == TextRole.TABLE_HEADER -> config.fonts.body(BOLD_WEIGHT, false)
                isHeading(role) -> config.fonts.body(if (role <= TextRole.HEADING_3) SEMI else BOLD_WEIGHT, false)
                else -> config.fonts.body(REGULAR_WEIGHT, false)
            }
        if (palette != null) paint.color = if (role == TextRole.HEADING_6) palette.inkMuted else palette.ink
        return paint
    }

    /** Width of one body character, `charW` of TYP-04 and TYP-06: the mean advance of the lowercase Latin alphabet. */
    public fun bodyCharWidth(): Float = paintOf(TextRole.BODY).measureText(ALPHABET) / ALPHABET.length

    internal companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        private const val UNBOUNDED = 1 shl 20
        private const val MEASURE_CHARS = 45
        private const val SEMI = 600
        private const val REGULAR_WEIGHT = 400
        private const val BOLD_WEIGHT = 700
        private const val INLINE_CODE_SCALE = 0.88f
        private const val SCRIPT_SCALE = 0.7f
        private const val LH_H1 = 1.2f
        private const val LH_H2 = 1.25f
        private const val LH_H3 = 1.3f
        private const val LH_H4 = 1.35f
        private const val LH_H5 = 1.4f
        private const val LH_CODE = 1.45f
        private const val SCALE_H1 = 1.75f
        private const val SCALE_H2 = 1.45f
        private const val SCALE_H3 = 1.22f
        private const val SCALE_H4 = 1.08f
        private const val SCALE_H6 = 0.92f
        private const val SCALE_CODE = 0.88f
        private const val SCALE_CELL = 0.9f

        fun isHeading(role: TextRole): Boolean = role.ordinal in TextRole.HEADING_1.ordinal..TextRole.HEADING_6.ordinal

        /** TYP-07 heading scales, DESIGN section 3 for code and cells. */
        fun scaleOf(role: TextRole): Float =
            when (role) {
                TextRole.HEADING_1 -> SCALE_H1
                TextRole.HEADING_2 -> SCALE_H2
                TextRole.HEADING_3 -> SCALE_H3
                TextRole.HEADING_4 -> SCALE_H4
                TextRole.HEADING_5 -> 1.0f
                TextRole.HEADING_6 -> SCALE_H6
                TextRole.CODE -> SCALE_CODE
                TextRole.TABLE_CELL, TextRole.TABLE_HEADER -> SCALE_CELL
                TextRole.BODY -> 1.0f
            }

        /** TYP-04: balanced for headings and for measures under `45 x charW`. */
        fun balanced(
            config: TextStyleConfig,
            role: TextRole,
            widthPx: Int,
        ): Boolean = isHeading(role) || (role != TextRole.CODE && widthPx < MEASURE_CHARS * config.charWidthPx)

        /** TYP-06: justify only with hyphenation on and a measure of at least `45 x charW`. */
        fun justifies(
            config: TextStyleConfig,
            widthPx: Int,
        ): Boolean = config.justify && config.hyphenate && widthPx >= MEASURE_CHARS * config.charWidthPx
    }
}
