package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.tides.StubTideClient
import nl.vdzon.robbertsassistent.tides.TideClient
import nl.vdzon.robbertsassistent.tides.TideForecast
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

    private class FailingTideClient : TideClient {
        override fun forecast(hours: Int) = TideForecast(levels = emptyList(), extremes = emptyList(), error = "getijfout")
    }

    private fun provider(
        wind: WindForecastClient = StubWindForecastClient(),
        weather: WeatherClient = StubWeatherClient(),
        tide: TideClient = StubTideClient(),
        storage: WeatherMapStorage = InMemoryWeatherMapStorage(),
    ) = WeatherMapSectionProvider(wind, weather, tide, StubCoastMapImageBuilder(), storage)

    @Test
    fun `section levert precies één item met imageUrl bij succesvolle voorspellingen`() {
        val section = provider().section()

        assertEquals("weather-map", section.key)
        assertEquals(1, section.items.size)
        assertEquals("/api/v1/briefing/weather-map/morgen", section.items[0].imageUrl)
    }

    @Test
    fun `section slaat de gegenereerde PNG op onder de vaste sleutel morgen`() {
        val storage = InMemoryWeatherMapStorage()

        provider(storage = storage).section()

        assertTrue(storage.load("morgen") != null)
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

    @Test
    fun `section crasht niet en levert alsnog een item bij een getijfout`() {
        val section = provider(tide = FailingTideClient()).section()

        assertEquals(1, section.items.size)
    }

    @Test
    fun `section bevat Avond in de tekst i-p-v Middag`() {
        val section = provider().section()

        assertTrue(section.items[0].text.contains("Avond"))
        assertTrue(!section.items[0].text.contains("Middag"))
    }
}
