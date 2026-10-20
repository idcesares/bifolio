package dev.dcesares.bifolio.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NAV-08: what an external link is allowed to be, and which part of it is the real host. */
class ExternalLinkTest {
    private fun parse(url: String) = checkNotNull(parseExternalLink(url)) { "refused: $url" }

    private fun hostOf(url: String) = parse(url).hostTexts

    @Test
    fun onlyWebAndMailSchemesAreAccepted() {
        for (url in listOf("https://example.com", "http://example.com/a", "HTTPS://Example.com", "mailto:a@b.org")) {
            assertNotNull(url, parseExternalLink(url))
        }
        val refused =
            listOf(
                "javascript:alert(1)",
                "file:///sdcard/a.md",
                "intent://scan/#Intent;scheme=zxing;end",
                "content://media/external/1",
                "data:text/html,hi",
                "tel:+5511999999999",
                "sms:123",
                "ftp://example.com",
                "market://details?id=a",
                "android-app://com.a",
                "http:example.com",
                "https:/example.com",
                "//example.com",
                "example.com",
                "",
                "   ",
            )
        for (url in refused) assertNull(url, parseExternalLink(url))
    }

    @Test
    fun schemeIsLowercasedAndTheRestIsKept() {
        assertEquals("https://Example.com/Path?q=A#Frag", parse("HTTPS://Example.com/Path?q=A#Frag").url)
        assertEquals("https", parse("HtTpS://a.io").scheme)
    }

    @Test
    fun hostIsTheAuthorityWithoutPortOrPath() {
        assertEquals(listOf("example.com"), hostOf("https://example.com"))
        assertEquals(listOf("example.com"), hostOf("https://example.com:8443/a/b?c=d#e"))
        assertEquals(listOf("example.com"), hostOf("https://example.com?x=1"))
        assertEquals(listOf("example.com"), hostOf("https://example.com#top"))
        assertEquals(listOf("[2001:db8::1]"), hostOf("http://[2001:db8::1]:80/x"))
        assertEquals(listOf("192.168.0.1"), hostOf("http://192.168.0.1/"))
    }

    @Test
    fun userInfoNeverPassesForTheHost() {
        val link = parse("https://good.com@evil.com/login")
        assertEquals(listOf("evil.com"), link.hostTexts)
        assertTrue(link.hasUserInfo)
        assertEquals(listOf("evil.com"), hostOf("https://good.com:pw@evil.com"))
        assertEquals(listOf("c.com"), hostOf("https://a@b@c.com/"))
        assertFalse(parse("https://evil.com/a@good.com").hasUserInfo)
        assertEquals(listOf("evil.com"), hostOf("https://evil.com/a@good.com"))
        assertEquals(listOf("evil.com"), hostOf("https://evil.com?u=good.com@x"))
    }

    @Test
    fun backslashEndsTheAuthorityLikeABrowserReadsIt() {
        val link = parse("https://evil.com\\@good.com/")
        assertEquals(listOf("evil.com"), link.hostTexts)
        assertFalse(link.hasUserInfo)
        // The string that is shown is the string that is opened.
        assertEquals("https://evil.com/@good.com/", link.url)
    }

    @Test
    fun hostRangeIndexesTheShownUrl() {
        val link = parse("https://user@Example.com:81/p")
        val range = link.hostRanges.single()
        assertEquals("Example.com", link.url.substring(range))
    }

    @Test
    fun malformedAuthoritiesAreRefused() {
        val refused =
            listOf(
                "https://",
                "https:///path",
                "https://@",
                "https://user@/",
                "https://host:port/",
                "https://host:99999999999/",
                "https://[::1/",
                "https://ex ample.com",
                "https://exa\tmple.com",
                "https://example.com/\u0000",
                "https://exa%6Dple.com",
                "https://exa<mple.com",
                "https://‮example.com",
                "https://exam​ple.com",
            )
        for (url in refused) assertNull(url, parseExternalLink(url))
    }

    @Test
    fun surroundingWhitespaceIsTrimmedButInnerWhitespaceRefuses() {
        assertEquals("https://a.io/x", parse("  https://a.io/x \n").url)
        assertNull(parseExternalLink("https://a.io/x y"))
    }

    @Test
    fun internationalHostsAreShownAndOpenedInTheirAsciiForm() {
        val link = parse("https://exämple.com/path")
        assertTrue(link.isInternational)
        assertEquals("https://xn--exmple-cua.com/path", link.url)
        assertEquals(listOf("xn--exmple-cua.com"), link.hostTexts)
        assertEquals("exämple.com", link.unicodeHost)
    }

    @Test
    fun lookAlikeHostIsNotShownAsTheLatinOne() {
        // Cyrillic a (U+0430) in place of the Latin a.
        val link = parse("https://аpple.com")
        assertTrue(link.isInternational)
        assertTrue(link.url.startsWith("https://xn--"))
        assertFalse(link.url.contains("pple.com") && link.url.contains("а"))
    }

    @Test
    fun punycodeWrittenByTheDocumentIsFlaggedToo() {
        val link = parse("https://xn--exmple-cua.com")
        assertTrue(link.isInternational)
        assertEquals("exämple.com", link.unicodeHost)
        assertFalse(parse("https://example.com").isInternational)
        assertNull(parse("https://example.com").unicodeHost)
    }

    @Test
    fun mailAddressDomainIsTheEmphasizedPart() {
        val link = parse("mailto:Ana@Example.com?subject=Hi")
        assertEquals("mailto", link.scheme)
        assertEquals(listOf("Example.com"), link.hostTexts)
        assertFalse(link.hasUserInfo)
    }

    @Test
    fun everyRecipientDomainOfAMailLinkIsEmphasized() {
        assertEquals(listOf("a.com", "b.org"), hostOf("mailto:x@a.com,y@b.org"))
        assertEquals(emptyList<String>(), hostOf("mailto:?subject=Hello"))
        assertNull(parseExternalLink("mailto:"))
        assertNull(parseExternalLink("mailto:a b@c.com"))
    }

    @Test
    fun veryLongUrlsKeepEveryCharacter() {
        val path = "a".repeat(3000)
        val link = parse("https://example.com/$path")
        assertEquals("https://example.com/$path", link.url)
        assertEquals(listOf("example.com"), link.hostTexts)
    }
}
