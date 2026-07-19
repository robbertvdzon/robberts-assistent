package nl.vdzon.robbertsassistent.news

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseFeed`-conversie zonder HTTP — geen precedent in deze repo voor het mocken
 * van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`).
 */
class RssNewsClientTest {

    @Test
    fun `parseFeed leest titel, link en pubDate uit RSS-items`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>NOS Nieuws</title>
                <item>
                  <title><![CDATA[Eerste artikel]]></title>
                  <link>https://nos.nl/l/1</link>
                  <pubDate>Sun, 19 Jul 2026 17:56:32 +0200</pubDate>
                </item>
                <item>
                  <title><![CDATA[Tweede artikel]]></title>
                  <link>https://nos.nl/l/2</link>
                  <pubDate>Sun, 19 Jul 2026 16:30:00 +0200</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = RssNewsClient.parseFeed(xml)

        assertNull(feed.error)
        assertEquals(2, feed.items.size)
        assertEquals("Eerste artikel", feed.items[0].title)
        assertEquals("https://nos.nl/l/1", feed.items[0].link)
        assertTrue(feed.items[0].publishedAt != null)
    }

    @Test
    fun `parseFeed slaat items zonder titel of link over`() {
        val xml = """
            <rss version="2.0"><channel>
              <item><title>Zonder link</title></item>
              <item><link>https://nos.nl/l/3</link></item>
              <item><title>Compleet</title><link>https://nos.nl/l/4</link></item>
            </channel></rss>
        """.trimIndent()

        val feed = RssNewsClient.parseFeed(xml)

        assertEquals(1, feed.items.size)
        assertEquals("Compleet", feed.items[0].title)
    }

    @Test
    fun `parseFeed geeft duidelijke melding bij ongeldige XML`() {
        val feed = RssNewsClient.parseFeed("dit is geen xml")

        assertTrue(feed.items.isEmpty())
        assertTrue(feed.error?.contains("Kon de nieuws-XML niet lezen") == true, "${feed.error}")
    }

    @Test
    fun `parseFeed weert een DOCTYPE (XXE-hardening) af in plaats van te crashen`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>&xxe;</title><link>https://nos.nl/l/5</link></item></channel></rss>
        """.trimIndent()

        val feed = RssNewsClient.parseFeed(xml)

        assertTrue(feed.items.isEmpty())
        assertTrue(feed.error != null, "een DOCTYPE moet de parse laten mislukken, niet stilzwijgend de entity oplossen")
    }
}
