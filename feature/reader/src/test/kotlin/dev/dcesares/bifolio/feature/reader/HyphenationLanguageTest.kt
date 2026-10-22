package dev.dcesares.bifolio.feature.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HyphenationLanguageTest {
    @Test
    fun `TYP-05 the document language wins over the app locale`() {
        assertEquals("pt-BR", hyphenationTag("pt-BR", "en-US"))
    }

    @Test
    fun `TYP-05 no document language falls back to the app locale`() {
        assertEquals("en-US", hyphenationTag(null, "en-US"))
    }

    @Test
    fun `TYP-05 a blank or malformed tag falls back to the app locale`() {
        assertEquals("en-US", hyphenationTag("", "en-US"))
        assertEquals("en-US", hyphenationTag("   ", "en-US"))
        assertEquals("en-US", hyphenationTag("!!", "en-US"))
    }

    @Test
    fun `TYP-05 an underscore tag is read as BCP 47`() {
        assertEquals("pt-BR", hyphenationTag("pt_BR", "en-US"))
    }
}
