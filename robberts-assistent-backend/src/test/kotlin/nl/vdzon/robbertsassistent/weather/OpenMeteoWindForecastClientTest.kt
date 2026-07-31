package nl.vdzon.robbertsassistent.weather

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseForecast`-conversie plus de ophaalstrategie (retry, TTL-cache,
 * last-known-good), zelfde patroon als `OpenMeteoWeatherClientTest`.
 */
class OpenMeteoWindForecastClientTest {

    private var now: Instant = Instant.parse("2026-07-31T10:00:00Z")

    private fun client(http: FakeHttpClient) = OpenMeteoWindForecastClient(
        httpClient = http,
        now = { now },
        sleeper = { },
    )

    private fun windJson(count: Int): String {
        val zone = ZoneId.of("Europe/Amsterdam")
        val start = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val times = (0 until count).joinToString(",") { "\"${formatter.format(start.plusHours(it.toLong()))}\"" }
        val speeds = (0 until count).joinToString(",") { "24.0" }
        val dirs = (0 until count).joinToString(",") { "270.0" }
        return """{"hourly": {"time": [$times], "wind_speed_10m": [$speeds], "wind_direction_10m": [$dirs]}}"""
    }

    @Test
    fun `probeert bij HTTP 503 opnieuw en stopt na drie pogingen met de bestaande foutmelding`() {
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(503))

        val forecast = client(http).hourlyForecast(4)

        assertEquals(3, http.calls)
        assertEquals("Kon Open-Meteo-wind niet ophalen (HTTP 503).", forecast.error)
    }

    @Test
    fun `probeert niet opnieuw bij een gewone 4xx`() {
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(400))

        val forecast = client(http).hourlyForecast(4)

        assertEquals(1, http.calls)
        assertEquals("Kon Open-Meteo-wind niet ophalen (HTTP 400).", forecast.error)
    }

    @Test
    fun `valt bij een mislukte ophaling terug op de laatst geslaagde windvoorspelling`() {
        val http = FakeHttpClient(
            FakeHttpClient.Reply.Status(200, windJson(4)),
            FakeHttpClient.Reply.Status(503),
        )
        val client = client(http)

        val fresh = client.hourlyForecast(4)
        val fetchedAt = now
        now = now.plus(Duration.ofHours(1))
        val stale = client.hourlyForecast(4)

        assertFalse(fresh.stale)
        assertNull(stale.error)
        assertTrue(stale.stale)
        assertEquals(fetchedAt, stale.fetchedAt)
    }

    @Test
    fun `deelt de call binnen de TTL`() {
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(200, windJson(6)))
        val client = client(http)

        client.hourlyForecast(6)
        now = now.plus(Duration.ofMinutes(5))
        val second = client.hourlyForecast(2)

        assertEquals(1, http.calls)
        assertEquals(2, second.hours.size)
    }

    @Test
    fun `parseForecast houdt alleen uren vanaf nu over, oplopend`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val now = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val times = listOf(now.minusHours(1), now, now.plusHours(1)).map { timeFormatter.format(it) }
        val timesJson = times.joinToString(",") { "\"$it\"" }
        val json = jacksonObjectMapper().readTree(
            """
            {
              "hourly": {
                "time": [$timesJson],
                "wind_speed_10m": [10.0, 24.0, 30.0],
                "wind_direction_10m": [90.0, 270.0, 315.0]
              }
            }
            """.trimIndent(),
        )

        val forecast = OpenMeteoWindForecastClient.parseForecast(json)

        assertNull(forecast.error)
        assertEquals(2, forecast.hours.size, "het uur van 1 uur geleden moet eruit gefilterd zijn")
        assertEquals(24.0, forecast.hours[0].speedKn)
        assertEquals(270.0, forecast.hours[0].directionDeg)
    }

    @Test
    fun `parseForecast geeft duidelijke melding bij lege data`() {
        val json = jacksonObjectMapper().readTree("""{"hourly": {}}""")

        val forecast = OpenMeteoWindForecastClient.parseForecast(json)

        assertTrue(forecast.hours.isEmpty())
        assertEquals("Open-Meteo gaf geen windvoorspellingsdata terug.", forecast.error)
    }
}
