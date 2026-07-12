package nl.vdzon.robbertsassistent.assistant.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dekt de pure `htmlToPlainText`-conversie zonder HTTP — geen precedent in deze repo voor het
 * mocken van `java.net.http.HttpClient` (zie o.a. `GitHubActionsClientTest` in softwarefactory).
 */
class WindToolsTest {

    @Test
    fun `strip script- en style-blokken en tags, decodeert entities, comprimeert whitespace`() {
        val html = """
            <html><head><style>body{color:red}</style></head>
            <body>
              <script>console.log('x')</script>
              <div class="wind">Wind: 16&nbsp;kts &amp; oplopend</div>
            </body></html>
        """.trimIndent()

        val text = WindTools.htmlToPlainText(html)

        assertEquals("Wind: 16 kts & oplopend", text)
        assertFalse(text.contains("console.log"))
        assertFalse(text.contains("color:red"))
    }

    @Test
    fun `knipt af op de maximumlengte`() {
        val longText = "x".repeat(10_000)

        val text = WindTools.htmlToPlainText("<p>$longText</p>")

        assertTrue(text.length <= 4000)
    }
}
