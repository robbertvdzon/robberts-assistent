package nl.vdzon.robbertsassistent.automower

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseMowers`-conversie zonder HTTP — geen precedent in deze repo voor het
 * mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`). De fixture is gebaseerd op
 * een echte respons (velden herleid, waarden ingekort).
 */
class HusqvarnaAutomowerClientTest {

    @Test
    fun `parseMowers leest naam, model, activiteit, status, batterij en verbinding`() {
        val json = jacksonObjectMapper().readTree(
            """
            {
              "data": [
                {
                  "type": "mower",
                  "id": "abc-123",
                  "attributes": {
                    "system": {"name": "Van der zon 310E", "model": "Husqvarna Automower® 310E NERA"},
                    "battery": {"batteryPercent": 100},
                    "mower": {"mode": "HOME", "activity": "PARKED_IN_CS", "state": "RESTRICTED", "errorCode": 0},
                    "metadata": {"connected": true, "statusTimestamp": 1784483492638}
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val mowers = HusqvarnaAutomowerClient.parseMowers(json)

        assertEquals(1, mowers.size)
        val mower = mowers[0]
        assertEquals("Van der zon 310E", mower.name)
        assertEquals("Husqvarna Automower® 310E NERA", mower.model)
        assertEquals("PARKED_IN_CS", mower.activity)
        assertEquals("RESTRICTED", mower.state)
        assertEquals(100, mower.batteryPercent)
        assertEquals(0, mower.errorCode)
        assertTrue(mower.connected)
    }

    @Test
    fun `parseMowers geeft een lege lijst bij een lege data-array`() {
        val json = jacksonObjectMapper().readTree("""{"data": []}""")

        assertTrue(HusqvarnaAutomowerClient.parseMowers(json).isEmpty())
    }

    @Test
    fun `parseMowers herkent een fout via errorCode en niet-verbonden via connected`() {
        val json = jacksonObjectMapper().readTree(
            """
            {
              "data": [
                {
                  "attributes": {
                    "system": {"name": "Maaier", "model": "Automower"},
                    "battery": {"batteryPercent": 12},
                    "mower": {"mode": "HOME", "activity": "STOPPED_IN_GARDEN", "state": "ERROR", "errorCode": 5},
                    "metadata": {"connected": false}
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val mower = HusqvarnaAutomowerClient.parseMowers(json).single()

        assertEquals(5, mower.errorCode)
        assertFalse(mower.connected)
    }

    @Test
    fun `activityDescription en stateDescription kennen de gangbare waarden`() {
        assertEquals("maait", activityDescription("MOWING"))
        assertEquals("geparkeerd in laadstation", activityDescription("PARKED_IN_CS"))
        assertEquals("ONBEKENDE_WAARDE", activityDescription("ONBEKENDE_WAARDE"))
        assertEquals("actief", stateDescription("IN_OPERATION"))
        assertEquals("beperkt door schema", stateDescription("RESTRICTED"))
    }
}
