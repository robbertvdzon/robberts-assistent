package nl.vdzon.robbertsassistent.airquality

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenMeteoAirQualityClientTest {

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
                "european_aqi": [10, 25, 45],
                "pm10": [8.0, 15.0, 22.0],
                "pm2_5": [4.0, 8.0, 12.0],
                "uv_index": [0.0, 4.0, 6.5],
                "birch_pollen": [0.0, 0.0, 0.0],
                "grass_pollen": [5.0, 12.0, 20.0],
                "ragweed_pollen": [0.0, 0.0, 0.0]
              }
            }
            """.trimIndent(),
        )

        val forecast = OpenMeteoAirQualityClient.parseForecast(json)

        assertNull(forecast.error)
        assertEquals(2, forecast.hours.size, "het uur van 1 uur geleden moet eruit gefilterd zijn")
        assertEquals(25, forecast.hours[0].europeanAqi)
        assertEquals(6.5, forecast.hours[1].uvIndex)
        assertEquals(20.0, forecast.hours[1].grassPollen)
    }

    @Test
    fun `parseForecast geeft duidelijke melding bij lege data`() {
        val json = jacksonObjectMapper().readTree("""{"hourly": {}}""")

        val forecast = OpenMeteoAirQualityClient.parseForecast(json)

        assertTrue(forecast.hours.isEmpty())
        assertEquals("Open-Meteo gaf geen luchtkwaliteitsdata terug.", forecast.error)
    }

    @Test
    fun `europeanAqiDescription en uvIndexDescription kennen de gangbare categorieen`() {
        assertEquals("goed", europeanAqiDescription(10))
        assertEquals("matig", europeanAqiDescription(50))
        assertEquals("extreem slecht", europeanAqiDescription(150))
        assertEquals("laag", uvIndexDescription(1.0))
        assertEquals("hoog", uvIndexDescription(7.0))
        assertEquals("extreem", uvIndexDescription(12.0))
    }
}
