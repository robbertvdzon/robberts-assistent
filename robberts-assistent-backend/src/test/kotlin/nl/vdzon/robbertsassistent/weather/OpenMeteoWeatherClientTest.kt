package nl.vdzon.robbertsassistent.weather

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseForecast`-conversie zonder HTTP — geen precedent in deze repo voor het
 * mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`).
 */
class OpenMeteoWeatherClientTest {

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
