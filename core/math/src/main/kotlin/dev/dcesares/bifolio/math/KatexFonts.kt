package dev.dcesares.bifolio.math

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.hrm.latex.renderer.model.LatexFontFamilies

/**
 * The KaTeX fonts (SIL OFL-1.1, from `latex-fonts-katex`), read from the assets that library ships. They are handed
 * to the renderer up front, so the first measurement already uses them. The renderer's own loader is asynchronous
 * and has no ready signal; measuring before it finishes gave different widths for 20 of 20 formulas (ADR-0006).
 */
internal object KatexFonts {
    internal const val ROOT = "composeResources/com.hrm.latex.fonts.katex.resources/font/"

    fun load(assets: AssetManager): LatexFontFamilies {
        fun font(
            name: String,
            weight: FontWeight = FontWeight.Normal,
            style: FontStyle = FontStyle.Normal,
        ) = Font("$ROOT$name.ttf", assets, weight, style)

        fun family(vararg fonts: Font) = FontFamily(*fonts)
        val bold = FontWeight.Bold
        val italic = FontStyle.Italic
        return LatexFontFamilies(
            main =
                family(
                    font("katex_main_regular"),
                    font("katex_main_bold", bold),
                    font("katex_main_italic", style = italic),
                    font("katex_main_bolditalic", bold, italic),
                ),
            math =
                family(
                    font("katex_math_italic", style = italic),
                    font("katex_math_bolditalic", bold, italic),
                    font("katex_math_italic"),
                ),
            ams = family(font("katex_ams_regular")),
            sansSerif =
                family(
                    font("katex_sansserif_regular"),
                    font("katex_sansserif_bold", bold),
                    font("katex_sansserif_italic", style = italic),
                ),
            monospace = family(font("katex_typewriter_regular")),
            caligraphic = family(font("katex_caligraphic_regular"), font("katex_caligraphic_bold", bold)),
            fraktur = family(font("katex_fraktur_regular"), font("katex_fraktur_bold", bold)),
            script = family(font("katex_script_regular")),
            size1 = family(font("katex_size1_regular")),
            size2 = family(font("katex_size2_regular")),
            size3 = family(font("katex_size3_regular")),
            size4 = family(font("katex_size4_regular")),
        )
    }
}
