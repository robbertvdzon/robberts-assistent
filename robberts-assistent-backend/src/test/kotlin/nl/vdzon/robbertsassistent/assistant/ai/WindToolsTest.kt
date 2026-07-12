package nl.vdzon.robbertsassistent.assistant.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

        assertTrue(text.length <= 6000)
    }

    @Test
    fun `compassPoint zet graden om naar 16-punts kompasrichting`() {
        assertEquals("N", WindTools.compassPoint(0.0))
        assertEquals("ONO", WindTools.compassPoint(60.0))
        assertEquals("O", WindTools.compassPoint(90.0))
        assertEquals("Z", WindTools.compassPoint(180.0))
        assertEquals("N", WindTools.compassPoint(359.0))
    }

    @Test
    fun `openMeteoResponseToText houdt alleen de checkpoint-uren over`() {
        val objectMapper = jacksonObjectMapper()
        val json = objectMapper.readTree(
            """
            {
              "hourly": {
                "time": ["2026-07-12T07:00", "2026-07-12T08:00", "2026-07-12T20:00", "2026-07-13T08:00"],
                "wind_speed_10m": [6.8, 6.8, 10.1, 5.8],
                "wind_gusts_10m": [14.0, 14.0, 20.2, 11.9],
                "wind_direction_10m": [62.0, 62.0, 47.0, 53.0]
              }
            }
            """.trimIndent(),
        )

        val text = WindTools.openMeteoResponseToText(json)
        val lines = text.lines()

        // 07:00 is geen checkpoint-uur; 08:00 (beide dagen) en 20:00 wel.
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("2026-07-12 08:00"))
        assertTrue(lines[0].contains("6.8 kts"))
        assertTrue(lines[0].contains("windstoten 14.0 kts"))
        assertTrue(lines[1].contains("2026-07-12 20:00"))
        assertTrue(lines[2].contains("2026-07-13 08:00"))
    }

    @Test
    fun `openMeteoResponseToText geeft duidelijke melding bij lege data`() {
        val objectMapper = jacksonObjectMapper()
        val json = objectMapper.readTree("""{"hourly": {}}""")

        assertEquals("Open-Meteo gaf geen voorspellingsdata terug.", WindTools.openMeteoResponseToText(json))
    }
}
