package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.airquality.StubAirQualityClient
import kotlin.test.Test
import kotlin.test.assertTrue

class AirQualityToolsTest {

    @Test
    fun `getAirQualityAndUvForecast geeft AQI, fijnstof en UV op basis van de stub`() {
        val tools = AirQualityTools(StubAirQualityClient())

        val result = tools.getAirQualityAndUvForecast()

        assertTrue(result.contains("AQI 25 (redelijk)"), result)
        assertTrue(result.contains("UV-index 4.0 (matig)"), result)
        assertTrue(result.contains("PM10 15.0"), result)
    }

    @Test
    fun `getPollenForecast geeft pollenwaarden op basis van de stub`() {
        val tools = AirQualityTools(StubAirQualityClient())

        val result = tools.getPollenForecast()

        assertTrue(result.contains("graspollen 12.0"), result)
    }
}
