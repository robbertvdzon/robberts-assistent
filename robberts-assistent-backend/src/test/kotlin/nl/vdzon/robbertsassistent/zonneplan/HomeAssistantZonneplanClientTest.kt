package nl.vdzon.robbertsassistent.zonneplan

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Dekt de pure `parseCurrentPower`/`parseYesterdayMaxKwh`-conversies zonder HTTP — zelfde patroon
 * als `HusqvarnaAutomowerClientTest` (geen precedent in deze repo voor het mocken van
 * `java.net.http.HttpClient`). Fixtures zijn gebaseerd op echte Home Assistant REST-API-responses.
 */
class HomeAssistantZonneplanClientTest {

    @Test
    fun `parseCurrentPower leest het huidige vermogen en rondt af`() {
        val json = jacksonObjectMapper().readTree(
            """{"entity_id": "sensor.zonneplan_one_optimized_omvormer_laatst_gemeten_waarde", "state": "1128.4"}""",
        )

        assertEquals(1128, HomeAssistantZonneplanClient.parseCurrentPower(json))
    }

    @Test
    fun `parseCurrentPower geeft null bij unavailable of unknown`() {
        val unavailable = jacksonObjectMapper().readTree("""{"state": "unavailable"}""")
        val unknown = jacksonObjectMapper().readTree("""{"state": "unknown"}""")

        assertNull(HomeAssistantZonneplanClient.parseCurrentPower(unavailable))
        assertNull(HomeAssistantZonneplanClient.parseCurrentPower(unknown))
    }

    @Test
    fun `parseYesterdayMaxKwh neemt de hoogste waarde vlak voor de dagreset`() {
        val json = jacksonObjectMapper().readTree(
            """
            [
              [
                {"state": "0.0", "last_changed": "2026-07-23T00:00:05+00:00"},
                {"state": "3.21", "last_changed": "2026-07-23T10:00:00+00:00"},
                {"state": "12.4", "last_changed": "2026-07-23T18:00:00+00:00"},
                {"state": "0.0", "last_changed": "2026-07-24T00:00:03+00:00"}
              ]
            ]
            """.trimIndent(),
        )

        assertEquals(12.4, HomeAssistantZonneplanClient.parseYesterdayMaxKwh(json))
    }

    @Test
    fun `parseYesterdayMaxKwh geeft null bij een lege of ontbrekende historie`() {
        val empty = jacksonObjectMapper().readTree("""[[]]""")
        val missing = jacksonObjectMapper().readTree("""[]""")

        assertNull(HomeAssistantZonneplanClient.parseYesterdayMaxKwh(empty))
        assertNull(HomeAssistantZonneplanClient.parseYesterdayMaxKwh(missing))
    }

    @Test
    fun `parseYesterdayMaxKwh negeert unavailable-states tussen geldige waarden`() {
        val json = jacksonObjectMapper().readTree(
            """[[{"state": "0.0"}, {"state": "unavailable"}, {"state": "4.82"}]]""",
        )

        assertEquals(4.82, HomeAssistantZonneplanClient.parseYesterdayMaxKwh(json))
    }
}
