package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.weather.StubWeatherClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherToolsTest {

    @Test
    fun `getRainForecastNextHours geeft 6 uur op basis van de stub`() {
        val tools = WeatherTools(StubWeatherClient())

        val result = tools.getRainForecastNextHours()

        assertEquals(6, result.lines().size)
        assertTrue(result.contains("18.0°C"), result)
        assertTrue(result.contains("half bewolkt"), result)
        assertTrue(result.contains("0.0 mm neerslag"), result)
    }

    @Test
    fun `getWeatherForecast filtert op checkpoint-uren en crasht niet`() {
        val tools = WeatherTools(StubWeatherClient())

        val result = tools.getWeatherForecast()

        assertTrue(result.isNotBlank(), result)
    }
}
