package dev.dcesares.bifolio.feature.reader

import dev.dcesares.bifolio.parse.MarkdownParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** SYN-01 and the internal half of NAV-08: `#slug` links resolve to top-level block indices. */
class SlugIndexTest {
    private val doc =
        MarkdownParser.parse(
            "# Intro\n\ntext\n\n## Café au lait\n\nmore\n\n> ## Quoted head\n\n- ## In list\n\n## Intro\n",
        )
    private val index = SlugIndex(doc.blocks)

    @Test
    fun findsTopLevelHeadings() {
        assertEquals(0, index.blockIndexOf("#intro"))
    }

    @Test
    fun duplicateSlugsResolveToTheirOwnSuffixedId() {
        val last = doc.blocks.lastIndex
        assertEquals(last, index.blockIndexOf("#intro-1"))
    }

    @Test
    fun percentEncodedAndCaseDifferingFragmentsResolve() {
        val expected = index.blockIndexOf("#café-au-lait")
        assertEquals(expected, index.blockIndexOf("#caf%C3%A9-au-lait"))
        assertEquals(0, index.blockIndexOf("#Intro"))
    }

    @Test
    fun nestedHeadingsResolveToTheirContainer() {
        val quote = index.blockIndexOf("#quoted-head")
        val list = index.blockIndexOf("#in-list")
        assertEquals(true, quote != null && list != null && quote < list)
    }

    @Test
    fun unknownAndExternalDestinationsAreNull() {
        assertNull(index.blockIndexOf("#nope"))
        assertNull(index.blockIndexOf("https://example.org/#intro"))
    }

    @Test
    fun aLiteralPlusStaysAPlusAndBrokenEscapesDoNotThrow() {
        assertNull(index.blockIndexOf("#a+b"))
        assertNull(index.blockIndexOf("#%E0%A4%A"))
        assertNull(index.blockIndexOf("#"))
    }
}
