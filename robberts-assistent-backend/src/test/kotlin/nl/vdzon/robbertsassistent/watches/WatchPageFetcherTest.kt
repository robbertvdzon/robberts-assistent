package nl.vdzon.robbertsassistent.watches

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchPageFetcherTest {

    @Test
    fun `htmlToPlainText verwijdert script tags`() {
        val html = "<html><script>alert('test');</script><body>Hello</body></html>"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertFalse(result.contains("alert"))
        assertTrue(result.contains("Hello"))
    }

    @Test
    fun `htmlToPlainText verwijdert style tags`() {
        val html = "<html><style>.red { color: red; }</style><body>World</body></html>"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertFalse(result.contains("color"))
        assertTrue(result.contains("World"))
    }

    @Test
    fun `htmlToPlainText verwijdert HTML tags`() {
        val html = "<div><p>Paragraph <strong>bold</strong> text</p></div>"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("Paragraph bold text", result)
    }

    @Test
    fun `htmlToPlainText decodeert nbsp entity`() {
        val html = "Hello&nbsp;World"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("Hello World", result)
    }

    @Test
    fun `htmlToPlainText decodeert amp entity`() {
        val html = "Tom &amp; Jerry"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("Tom & Jerry", result)
    }

    @Test
    fun `htmlToPlainText decodeert lt en gt entities`() {
        val html = "a &lt; b &gt; c"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("a < b > c", result)
    }

    @Test
    fun `htmlToPlainText decodeert quot entity`() {
        val html = "Zeg &quot;hallo&quot;"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("Zeg \"hallo\"", result)
    }

    @Test
    fun `htmlToPlainText decodeert apos entities`() {
        val html = "It&#39;s nice &apos;today&apos;"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("It's nice 'today'", result)
    }

    @Test
    fun `htmlToPlainText comprimeert whitespace`() {
        val html = "Hello    \n\n   World    \t\t  Test"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("Hello World Test", result)
    }

    @Test
    fun `htmlToPlainText trimt resultaat`() {
        val html = "   Hello World   "
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals("Hello World", result)
    }

    @Test
    fun `htmlToPlainText beperkt lengte tot 8000 karakters`() {
        val longContent = "a".repeat(10000)
        val html = "<p>$longContent</p>"
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertEquals(8000, result.length)
    }

    @Test
    fun `htmlToPlainText handelt multiline script correct af`() {
        val html = """
            <html>
            <script type="text/javascript">
                function test() {
                    console.log('test');
                }
            </script>
            <body>Content here</body>
            </html>
        """.trimIndent()
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertFalse(result.contains("function"))
        assertFalse(result.contains("console"))
        assertTrue(result.contains("Content here"))
    }

    @Test
    fun `htmlToPlainText handelt echte webpagina-achtige HTML`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Productpagina</title>
                <style>body { margin: 0; }</style>
            </head>
            <body>
                <div class="product">
                    <h1>Aaltjes tegen slakken</h1>
                    <p class="price">€ 19,95</p>
                    <p class="stock">Niet op voorraad</p>
                </div>
                <script>trackPageView();</script>
            </body>
            </html>
        """.trimIndent()
        val result = WatchPageFetcher.htmlToPlainText(html)
        assertTrue(result.contains("Aaltjes tegen slakken"))
        assertTrue(result.contains("€ 19,95"))
        assertTrue(result.contains("Niet op voorraad"))
        assertFalse(result.contains("trackPageView"))
        assertFalse(result.contains("margin"))
    }
}
