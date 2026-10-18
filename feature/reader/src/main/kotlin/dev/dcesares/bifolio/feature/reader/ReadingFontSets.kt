package dev.dcesares.bifolio.feature.reader

import android.content.res.AssetManager
import dev.dcesares.bifolio.data.FontChoice
import dev.dcesares.bifolio.designsystem.ReadingFont
import dev.dcesares.bifolio.text.AssetFontSet
import dev.dcesares.bifolio.text.FontSet

/** The bundled family the user picked, as the faces the paginator measures and the page draws (TYP-01). */
internal fun fontSetOf(
    choice: FontChoice,
    assets: AssetManager,
): FontSet {
    val font = choice.toReadingFont()
    return AssetFontSet(assets, font.uprightAsset, font.italicAsset, ReadingFont.MONO_ASSET)
}

public fun FontChoice.toReadingFont(): ReadingFont =
    when (this) {
        FontChoice.LITERATA -> ReadingFont.Literata
        FontChoice.SOURCE_SERIF -> ReadingFont.SourceSerif
        FontChoice.ATKINSON -> ReadingFont.Atkinson
    }
