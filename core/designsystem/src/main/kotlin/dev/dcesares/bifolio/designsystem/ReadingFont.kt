package dev.dcesares.bifolio.designsystem

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The bundled reading families (TYP-01, ADR-0009). Paths are inside the app assets; each family is a pair of
 * variable fonts (upright and italic) whose weight is a `wght` variation. Code is always JetBrains Mono.
 */
public enum class ReadingFont(
    public val uprightAsset: String,
    public val italicAsset: String,
) {
    Literata("fonts/Literata.ttf", "fonts/Literata-Italic.ttf"),
    SourceSerif("fonts/SourceSerif4.ttf", "fonts/SourceSerif4-Italic.ttf"),
    Atkinson("fonts/Atkinson.ttf", "fonts/Atkinson-Italic.ttf"),
    ;

    public companion object {
        public const val MONO_ASSET: String = "fonts/JetBrainsMono.ttf"
    }
}

private val weights = listOf(400, 500, 600, 700)

/** Compose family for [font], with the weights the type scale uses. */
public fun ReadingFont.family(assets: AssetManager): FontFamily =
    FontFamily(
        weights.flatMap { w ->
            listOf(
                variable(assets, uprightAsset, w, FontStyle.Normal),
                variable(assets, italicAsset, w, FontStyle.Italic),
            )
        },
    )

/** Compose family for code. */
public fun monoFamily(assets: AssetManager): FontFamily =
    FontFamily(weights.map { w -> variable(assets, ReadingFont.MONO_ASSET, w, FontStyle.Normal) })

private fun variable(
    assets: AssetManager,
    path: String,
    weight: Int,
    style: FontStyle,
): Font =
    Font(
        path = path,
        assetManager = assets,
        weight = FontWeight(weight),
        style = style,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )
