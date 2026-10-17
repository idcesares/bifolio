package dev.dcesares.bifolio.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Interim rendered-to-source mapping (ADR-0005 open point). */
class OffsetMapTest {
    @Test
    fun `markup between characters is skipped`() {
        val source = "**bold** text"
        val map = OffsetMap.build("bold text", source, 0, source.length)
        assertEquals(2, map[0])
        assertEquals(5, map[3])
        assertEquals(8, map[4])
        assertEquals(9, map[5])
        assertEquals(source.length, map[9])
    }

    @Test
    fun `plain text maps one to one`() {
        val source = "plain text"
        val map = OffsetMap.build(source, source, 0, source.length)
        assertEquals(source.indices.toList(), map.take(source.length))
    }

    @Test
    fun `characters absent from the source never break monotonicity or bounds`() {
        val source = "ab"
        val map = OffsetMap.build("a&&&b", source, 0, source.length)
        for (i in 1 until map.size) assertTrue(map[i] >= map[i - 1])
        assertTrue(map.all { it in 0..source.length })
    }

    @Test
    fun `respects the search window of a block inside a larger source`() {
        val source = "xx hello yy"
        val map = OffsetMap.build("hello", source, 3, 8)
        assertEquals(3, map[0])
        assertEquals(8, map[5])
    }
}
