package dev.dcesares.bifolio.data

/** Reading theme choice. A null choice follows the system dark mode (TYP-08). */
public enum class ThemeChoice { PAPER, SEPIA, DUSK, NIGHT }

/** How a page turn looks (MOT-02, MOT-03). */
public enum class TurnChoice { SLIDE, FADE, NONE }

/** Body face of the reading surface (TYP-01). Literata is the default. */
public enum class FontChoice { LITERATA, SOURCE_SERIF, ATKINSON }

public const val MIN_BODY_SP: Int = 14
public const val MAX_BODY_SP: Int = 28
public const val DEFAULT_BODY_SP: Int = 18
public const val DEFAULT_LINE_HEIGHT: Float = 1.5f
public val LINE_HEIGHTS: List<Float> = listOf(1.35f, 1.5f, 1.65f)
public val MARGIN_SCALES: List<Float> = listOf(0.75f, 1.0f, 1.35f)

/**
 * The global reading profile of UI-10 (one profile for every document). Build values through
 * [normalized]; the store applies it on every read and write.
 *
 * [columns] is the user's maximum (LAY-08), null for Auto.
 */
public data class AppearanceProfile(
    val theme: ThemeChoice? = null,
    val bodySp: Int = DEFAULT_BODY_SP,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val marginScale: Float = 1.0f,
    val columns: Int? = null,
    val justify: Boolean = false,
    val hyphenate: Boolean = true,
    val turn: TurnChoice = TurnChoice.SLIDE,
    val font: FontChoice = FontChoice.LITERATA,
    val hairline: Boolean = true,
    val reduceMotion: Boolean = false,
    /** Dynamic color for library and settings chrome only, never the reading surface (TYP-09). Off by default. */
    val dynamicColor: Boolean = false,
) {
    /** Clamps the size to 14..28sp, snaps line height and margins to their options and drops invalid columns. */
    public fun normalized(): AppearanceProfile =
        copy(
            bodySp = bodySp.coerceIn(MIN_BODY_SP, MAX_BODY_SP),
            lineHeight = nearest(LINE_HEIGHTS, lineHeight),
            marginScale = nearest(MARGIN_SCALES, marginScale),
            columns = columns?.takeIf { it >= 1 }?.coerceAtMost(MAX_COLUMN_CHOICE),
        )
}

private const val MAX_COLUMN_CHOICE = 3

private fun nearest(
    options: List<Float>,
    value: Float,
): Float = options.minBy { kotlin.math.abs(it - value) }
