package nl.vdzon.robbertsassistent.strava

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseActivities`-conversie zonder HTTP — geen precedent in deze repo voor het
 * mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`). De fixture is gebaseerd op
 * een echte respons (velden herleid).
 */
class StravaActivityClientTest {

    @Test
    fun `parseActivities leest naam, sport, tijd, afstand, duur, hartslag en trainer`() {
        val json = jacksonObjectMapper().readTree(
            """
            [
              {
                "name": "Ochtendrit",
                "type": "Ride",
                "sport_type": "Ride",
                "start_date": "2026-07-18T07:09:27Z",
                "distance": 0.0,
                "moving_time": 2811,
                "average_heartrate": 125.0,
                "trainer": true
              },
              {
                "name": "Rondje Heemskerk",
                "type": "Run",
                "sport_type": "Run",
                "start_date": "2026-07-16T18:00:00Z",
                "distance": 8200.0,
                "moving_time": 2520,
                "trainer": false
              }
            ]
            """.trimIndent(),
        )

        val activities = StravaActivityClient.parseActivities(json)

        assertEquals(2, activities.size)
        val ride = activities[0]
        assertEquals("Ochtendrit", ride.name)
        assertEquals("Ride", ride.sportType)
        assertEquals(Instant.parse("2026-07-18T07:09:27Z"), ride.startDate)
        assertEquals(0.0, ride.distanceKm)
        assertEquals(46, ride.movingTimeMinutes)
        assertEquals(125.0, ride.averageHeartrate)
        assertTrue(ride.trainer)

        val run = activities[1]
        assertEquals(8.2, run.distanceKm)
        assertEquals(42, run.movingTimeMinutes)
        assertNull(run.averageHeartrate)
        assertTrue(!run.trainer)
    }

    @Test
    fun `parseActivities slaat items zonder start_date over`() {
        val json = jacksonObjectMapper().readTree("""[{"name": "Zonder datum"}]""")

        assertTrue(StravaActivityClient.parseActivities(json).isEmpty())
    }
}
