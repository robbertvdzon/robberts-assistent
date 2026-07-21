package nl.vdzon.robbertsassistent.weather

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dekt de pure `parseForecast`-conversie zonder HTTP, zelfde patroon als `OpenMeteoWeatherClientTest`. */
class OpenMeteoWindForecastClientTest {

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
