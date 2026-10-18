package dev.dcesares.bifolio.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** UI-10, TYP-02, TYP-03, TYP-05, TYP-06, LAY-05: the global appearance profile and its limits. */
class AppearanceProfileTest {
    @Test
    fun `quick columns cycle Auto, 1, 2, 3 and back`() {
        val seen = generateSequence(AppearanceProfile()) { it.withNextColumns() }.take(5).map { it.columns }.toList()
        assertEquals(listOf(null, 1, 2, 3, null), seen)
    }

    @Test
    fun `quick theme cycles Auto and the four themes and back`() {
        val seen = generateSequence(AppearanceProfile()) { it.withNextTheme() }.take(6).map { it.theme }.toList()
        assertEquals(listOf(null) + ThemeChoice.entries + listOf(null), seen)
    }

    @Test
    fun `quick size steps stay inside the limits`() {
        assertEquals(MAX_BODY_SP, AppearanceProfile(bodySp = MAX_BODY_SP).withBodySpStep(1).bodySp)
        assertEquals(MIN_BODY_SP, AppearanceProfile(bodySp = MIN_BODY_SP).withBodySpStep(-1).bodySp)
        assertEquals(19, AppearanceProfile().withBodySpStep(1).bodySp)
    }

    @Test
    fun `defaults match the spec`() =
        runTest {
            val p = ReadingPreferences(InMemoryPreferences()).appearance.first()
            assertNull(p.theme)
            assertEquals(18, p.bodySp)
            assertEquals(1.5f, p.lineHeight)
            assertEquals(1.0f, p.marginScale)
            assertNull(p.columns)
            assertEquals(false, p.justify)
            assertEquals(true, p.hyphenate)
            assertEquals(TurnChoice.SLIDE, p.turn)
            assertEquals(FontChoice.LITERATA, p.font)
            assertEquals(true, p.hairline)
            assertEquals(false, p.reduceMotion)
            assertEquals(false, p.dynamicColor)
        }

    @Test
    fun `a change survives a new instance on the same store`() =
        runTest {
            val store = InMemoryPreferences()
            ReadingPreferences(store).updateAppearance {
                it.copy(
                    theme = ThemeChoice.SEPIA,
                    bodySp = 22,
                    columns = 2,
                    turn = TurnChoice.FADE,
                    justify = true,
                    font = FontChoice.ATKINSON,
                    dynamicColor = true,
                )
            }
            val p = ReadingPreferences(store).appearance.first()
            assertEquals(ThemeChoice.SEPIA, p.theme)
            assertEquals(22, p.bodySp)
            assertEquals(2, p.columns)
            assertEquals(TurnChoice.FADE, p.turn)
            assertEquals(FontChoice.ATKINSON, p.font)
            assertEquals(true, p.justify)
            assertEquals(true, p.dynamicColor)
        }

    @Test
    fun `values outside the spec are clamped or snapped (TYP-02, TYP-03, LAY-05)`() =
        runTest {
            val prefs = ReadingPreferences(InMemoryPreferences())
            prefs.updateAppearance { it.copy(bodySp = 99, lineHeight = 1.6f, marginScale = 5f, columns = 7) }
            val p = prefs.appearance.first()
            assertEquals(28, p.bodySp)
            assertEquals(1.65f, p.lineHeight)
            assertEquals(1.35f, p.marginScale)
            assertEquals(3, p.columns)
            prefs.updateAppearance { it.copy(bodySp = 3, lineHeight = 1.0f, marginScale = 0.1f, columns = 0) }
            val low = prefs.appearance.first()
            assertEquals(14, low.bodySp)
            assertEquals(1.35f, low.lineHeight)
            assertEquals(0.75f, low.marginScale)
            assertNull(low.columns)
        }

    @Test
    fun `unknown stored names fall back to the defaults`() =
        runTest {
            val store = InMemoryPreferences()
            store.edit { it[stringPreferencesKey("appearance_theme")] = "Neon" }
            assertNull(ReadingPreferences(store).appearance.first().theme)
        }
}
