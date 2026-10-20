package dev.dcesares.bifolio.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/** TYP-10: body text at least 7:1 and secondary text at least 4.5:1 in every theme (WCAG 2.x). */
class ContrastTest {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val all = ReadingThemeId.entries.map { ReadingPalettes.of(it) }

    @Test
    fun inkMeetsSevenToOneOnPage() {
        all.forEach { assertTrue(ratio(it.ink, it.page) >= 7.0, "${it.id} ink on page") }
    }

    @Test
    fun inkMeetsSevenToOneOnCodeSurface() {
        all.forEach { assertTrue(ratio(it.ink, it.codeSurface) >= 7.0, "${it.id} ink on code surface") }
    }

    @Test
    fun secondaryAccentAndLinkMeetFourPointFive() {
        all.forEach {
            assertTrue(ratio(it.inkMuted, it.page) >= 4.5, "${it.id} inkMuted")
            assertTrue(ratio(it.accent, it.page) >= 4.5, "${it.id} accent")
            assertTrue(ratio(it.link, it.page) >= 4.5, "${it.id} link")
        }
    }

    @Test
    fun searchHitsKeepInkAtFourPointFiveAndShowAgainstThePage() {
        all.forEach {
            val hit = it.searchHit.compositeOver(it.page)
            val focused = it.searchHitFocused.compositeOver(it.page)
            assertTrue(ratio(it.ink, hit) >= 4.5, "${it.id} ink on hit")
            assertTrue(ratio(it.ink, focused) >= 4.5, "${it.id} ink on focused hit")
            assertTrue(ratio(hit, it.page) >= 1.2, "${it.id} hit against page")
            assertTrue(ratio(focused, it.page) >= 1.5, "${it.id} focused hit against page")
            assertTrue(ratio(focused, hit) >= 1.1, "${it.id} focused against other hits")
            // Not the ==mark== tint (accent at 25%).
            assertTrue(it.searchHit != it.accent.copy(alpha = 0.25f), "${it.id} differs from mark")
        }
    }

    @Test
    fun typographyScaleFollowsDesignTable() {
        val t = readingTypography(ReadingPalettes.Paper)
        assertTrue(t.heading(1).fontSize.value > t.heading(2).fontSize.value)
        assertTrue(t.heading(2).fontSize.value > t.heading(3).fontSize.value)
        assertTrue(t.body.lineHeight.value == t.bodySize.value * 1.5f)
    }
}
