package dev.dcesares.bifolio.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingPreferencesTest {
    @Test
    fun `the mode is paged until it is changed and then survives a new instance on the same store`() =
        runTest {
            val store = InMemoryPreferences()
            assertEquals(ReadingMode.PAGED, ReadingPreferences(store).mode.first())
            ReadingPreferences(store).setMode(ReadingMode.SCROLL)
            assertEquals(ReadingMode.SCROLL, ReadingPreferences(store).mode.first())
        }
}
