package nl.vdzon.robbertsassistent.weather

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
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
 * Dekt de pure `parseForecast`-conversie plus de ophaalstrategie (retry, TTL-cache en
 * last-known-good) via een [FakeHttpClient]-testdouble, zonder netwerk.
 */
class OpenMeteoWeatherClientTest {

    /** Bestuurbare klok, zodat TTL en de 12-uursgrens zonder echte wachttijd te testen zijn. */
    private class TestClock(var instant: Instant = Instant.parse("2026-07-31T10:00:00Z")) {
        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }

    private val slept = mutableListOf<Long>()

    private fun client(clock: TestClock, http: FakeHttpClient) = OpenMeteoWeatherClient(
        httpClient = http,
        now = { clock.instant },
        sleeper = { slept += it },
    )

    /** Geldige Open-Meteo-respons met [count] uren vanaf het huidige uur. */
    private fun forecastJson(count: Int): String {
        val zone = ZoneId.of("Europe/Amsterdam")
        val start = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val times = (0 until count).joinToString(",") { "\"${formatter.format(start.plusHours(it.toLong()))}\"" }
        val values = (0 until count).joinToString(",") { "1.0" }
        val ints = (0 until count).joinToString(",") { "1" }
        return """
            {"hourly": {"time": [$times], "temperature_2m": [$values], "precipitation": [$values],
             "precipitation_probability": [$ints], "weathercode": [$ints]}}
        """.trimIndent()
    }

    // --- Retry, TTL-cache en last-known-good ---

    @Test
    fun `probeert bij HTTP 503 opnieuw en levert data zodra een poging slaagt`() {
        val http = FakeHttpClient(
            FakeHttpClient.Reply.Status(503),
            FakeHttpClient.Reply.Status(503),
            FakeHttpClient.Reply.Status(200, forecastJson(4)),
        )

        val forecast = client(TestClock(), http).hourlyForecast(3)

        assertEquals(3, http.calls, "maximaal 3 pogingen, en de derde slaagt")
        assertNull(forecast.error)
        assertFalse(forecast.stale)
        assertEquals(3, forecast.hours.size)
        assertEquals(listOf(500L, 2_000L), slept, "pauzes van ~0,5s en ~2s tussen de pogingen")
    }

    @Test
    fun `geeft na drie mislukte pogingen de bestaande foutmelding`() {
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(503))

        val forecast = client(TestClock(), http).hourlyForecast(3)

        assertEquals(3, http.calls)
        assertEquals("Kon Open-Meteo niet ophalen (HTTP 503).", forecast.error)
    }

    @Test
    fun `probeert niet opnieuw bij een gewone 4xx`() {
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(404))

        val forecast = client(TestClock(), http).hourlyForecast(3)

        assertEquals(1, http.calls, "opnieuw proberen helpt niet bij een 4xx")
        assertEquals("Kon Open-Meteo niet ophalen (HTTP 404).", forecast.error)
    }

    @Test
    fun `probeert wel opnieuw bij 429 en bij een netwerkfout`() {
        val rateLimited = FakeHttpClient(FakeHttpClient.Reply.Status(429))
        client(TestClock(), rateLimited).hourlyForecast(3)
        assertEquals(3, rateLimited.calls)

        val broken = FakeHttpClient(FakeHttpClient.Reply.Throws(IOException("verbinding weg")))
        val forecast = client(TestClock(), broken).hourlyForecast(3)
        assertEquals(3, broken.calls)
        assertEquals("Kon Open-Meteo niet ophalen: verbinding weg", forecast.error)
    }

    @Test
    fun `valt terug op de laatst geslaagde voorspelling en markeert die als verouderd`() {
        val clock = TestClock()
        val http = FakeHttpClient(
            FakeHttpClient.Reply.Status(200, forecastJson(4)),
            FakeHttpClient.Reply.Status(503),
        )
        val client = client(clock, http)

        val fresh = client.hourlyForecast(4)
        val fetchedAt = clock.instant
        clock.advance(Duration.ofMinutes(30))
        val stale = client.hourlyForecast(4)

        assertNull(fresh.error)
        assertFalse(fresh.stale)
        assertNull(stale.error, "last-known-good levert data i.p.v. een fout")
        assertTrue(stale.stale)
        assertEquals(fetchedAt, stale.fetchedAt)
        assertEquals(fresh.hours, stale.hours)
    }

    @Test
    fun `laat een last-known-good ouder dan 12 uur vervallen`() {
        val clock = TestClock()
        val http = FakeHttpClient(
            FakeHttpClient.Reply.Status(200, forecastJson(4)),
            FakeHttpClient.Reply.Status(503),
        )
        val client = client(clock, http)

        client.hourlyForecast(4)
        clock.advance(Duration.ofHours(13))
        val forecast = client.hourlyForecast(4)

        assertEquals("Kon Open-Meteo niet ophalen (HTTP 503).", forecast.error)
        assertTrue(forecast.hours.isEmpty())
    }

    @Test
    fun `doet binnen de TTL geen tweede HTTP-call en kapt toch correct af`() {
        val clock = TestClock()
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(200, forecastJson(6)))
        val client = client(clock, http)

        val first = client.hourlyForecast(6)
        clock.advance(Duration.ofMinutes(5))
        val second = client.hourlyForecast(2)

        assertEquals(1, http.calls, "tweede aanroep binnen de TTL hergebruikt de cache")
        assertEquals(6, first.hours.size)
        assertEquals(2, second.hours.size, "hours kapt ook bij een cachehit correct af")
        assertFalse(second.stale, "een TTL-cachehit is geen verouderde data")
    }

    @Test
    fun `haalt na het verlopen van de TTL opnieuw op`() {
        val clock = TestClock()
        val http = FakeHttpClient(FakeHttpClient.Reply.Status(200, forecastJson(4)))
        val client = client(clock, http)

        client.hourlyForecast(4)
        clock.advance(Duration.ofMinutes(11))
        client.hourlyForecast(4)

        assertEquals(2, http.calls)
    }

    @Test
    fun `parseForecast houdt alleen uren vanaf nu over, oplopend`() {
        val zone = ZoneId.of("Europe/Amsterdam")
        val now = LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val times = listOf(now.minusHours(1), now, now.plusHours(1), now.plusHours(2)).map { timeFormatter.format(it) }
        val timesJson = times.joinToString(",") { "\"$it\"" }
        val json = jacksonObjectMapper().readTree(
            """
            {
              "hourly": {
                "time": [$timesJson],
                "temperature_2m": [10.0, 11.0, 12.0, 13.0],
                "precipitation": [0.0, 0.1, 0.2, 0.0],
                "precipitation_probability": [0, 10, 40, 5],
                "weathercode": [0, 2, 61, 3]
              }
            }
            """.trimIndent(),
        )

        val forecast = OpenMeteoWeatherClient.parseForecast(json)

        assertNull(forecast.error)
        assertEquals(3, forecast.hours.size, "het uur van 1 uur geleden moet eruit gefilterd zijn")
        assertEquals(11.0, forecast.hours[0].temperatureC)
        assertEquals(40, forecast.hours[1].precipitationProbabilityPct)
        assertEquals(61, forecast.hours[1].weatherCode)
    }

    @Test
    fun `parseForecast geeft duidelijke melding bij lege data`() {
        val json = jacksonObjectMapper().readTree("""{"hourly": {}}""")

        val forecast = OpenMeteoWeatherClient.parseForecast(json)

        assertTrue(forecast.hours.isEmpty())
        assertEquals("Open-Meteo gaf geen voorspellingsdata terug.", forecast.error)
    }

    @Test
    fun `weatherCodeDescription kent de gangbare WMO-codes`() {
        assertEquals("helder", weatherCodeDescription(0))
        assertEquals("half bewolkt", weatherCodeDescription(2))
        assertEquals("regen", weatherCodeDescription(63))
        assertEquals("onweer met hagel", weatherCodeDescription(99))
        assertEquals("onbekend (123)", weatherCodeDescription(123))
    }
}
