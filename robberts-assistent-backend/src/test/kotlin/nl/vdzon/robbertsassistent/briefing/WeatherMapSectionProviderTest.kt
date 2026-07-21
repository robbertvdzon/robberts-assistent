package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.weather.StubWeatherClient
import nl.vdzon.robbertsassistent.weather.StubWindForecastClient
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WeatherForecast
import nl.vdzon.robbertsassistent.weather.WindForecast
import nl.vdzon.robbertsassistent.weather.WindForecastClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherMapSectionProviderTest {

    private class FailingWindClient : WindForecastClient {
        override fun hourlyForecast(hours: Int) = WindForecast(hours = emptyList(), error = "netwerkfout")
    }

    private class FailingWeatherClient : WeatherClient {
        override fun hourlyForecast(hours: Int) = WeatherForecast(hours = emptyList(), error = "netwerkfout")
    }

    private fun provider(
        wind: WindForecastClient = StubWindForecastClient(),
        weather: WeatherClient = StubWeatherClient(),
        storage: WeatherMapStorage = InMemoryWeatherMapStorage(),
    ) = WeatherMapSectionProvider(wind, weather, StubCoastMapImageBuilder(), storage)

    @Test
    fun `section levert twee items met imageUrl bij succesvolle voorspellingen`() {
        val section = provider().section()

        assertEquals("weather-map", section.key)
        assertEquals(2, section.items.size)
        assertTrue(section.items.all { it.imageUrl != null })
        assertEquals("/api/v1/briefing/weather-map/ochtend", section.items[0].imageUrl)
        assertEquals("/api/v1/briefing/weather-map/middag", section.items[1].imageUrl)
    }

    @Test
    fun `section slaat gegenereerde PNG's op onder vaste dagdeel-sleutels`() {
        val storage = InMemoryWeatherMapStorage()

        provider(storage = storage).section()

        assertTrue(storage.load("ochtend") != null)
        assertTrue(storage.load("middag") != null)
    }

    @Test
    fun `section faalt netjes zonder items bij een windfout`() {
        val section = provider(wind = FailingWindClient()).section()

        assertTrue(section.items.isEmpty())
        assertTrue(section.text.contains("netwerkfout"))
    }

    @Test
    fun `section faalt netjes zonder items bij een weerfout`() {
        val section = provider(weather = FailingWeatherClient()).section()

        assertTrue(section.items.isEmpty())
        assertTrue(section.text.contains("netwerkfout"))
    }
}
