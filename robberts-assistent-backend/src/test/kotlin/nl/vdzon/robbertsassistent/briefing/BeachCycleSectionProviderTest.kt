package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.google.CalendarEvent
import nl.vdzon.robbertsassistent.tides.StubTideClient
import nl.vdzon.robbertsassistent.weather.HourlyWeather
import nl.vdzon.robbertsassistent.weather.StubWindForecastClient
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WeatherForecast
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeachCycleSectionProviderTest {

    private class EmptyCalendarClient : CalendarClient {
        override fun upcoming(maxResults: Int) = emptyList<CalendarEvent>()
        override fun search(query: String) = emptyList<CalendarEvent>()
        override fun eventsInRange(from: Instant, to: Instant) = emptyList<CalendarEvent>()
    }

    private class DryWeatherClient : WeatherClient {
        override fun hourlyForecast(hours: Int): WeatherForecast {
            val now = Instant.now()
            return WeatherForecast((0 until hours).map { offset ->
                HourlyWeather(
                    time = now.plus(Duration.ofHours(offset.toLong())),
                    temperatureC = 15.0,
                    precipitationMm = 0.0,
                    precipitationProbabilityPct = 0,
                    weatherCode = 0,
                )
            })
        }
    }

    @Test
    fun `section bouwt tekst op met onderbouwing`() {
        val provider = BeachCycleSectionProvider(
            windForecastClient = StubWindForecastClient(speedKn = 10.0, directionDeg = 270.0),
            weatherClient = DryWeatherClient(),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val section = provider.section()

        assertEquals("beach", section.key)
        assertEquals("Strandfietsen", section.title)
        assertTrue(section.text.contains("10 kn"))
        assertTrue(section.text.contains("droog"))
        assertTrue(section.text.contains("laagwater"))
        assertTrue(!Regex("laagwater om \\d{2}:\\d{2}").containsMatchIn(section.text), "geen laagwatertijd meer in de tekst")
    }

    @Test
    fun `section toont regenhoeveelheid bij nat weer`() {
        val provider = BeachCycleSectionProvider(
            windForecastClient = StubWindForecastClient(speedKn = 10.0, directionDeg = 270.0),
            weatherClient = object : WeatherClient {
                override fun hourlyForecast(hours: Int): WeatherForecast {
                    val now = Instant.now()
                    return WeatherForecast((0 until hours).map { offset ->
                        HourlyWeather(
                            time = now.plus(Duration.ofHours(offset.toLong())),
                            temperatureC = 15.0,
                            precipitationMm = 2.5,
                            precipitationProbabilityPct = 80,
                            weatherCode = 61,
                        )
                    })
                }
            },
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val section = provider.section()

        assertTrue(section.text.contains("mm nat"))
    }

    @Test
    fun `section meldt een fout als een bron faalt`() {
        val provider = BeachCycleSectionProvider(
            windForecastClient = StubWindForecastClient(),
            weatherClient = object : WeatherClient {
                override fun hourlyForecast(hours: Int) = WeatherForecast(emptyList(), "kapot")
            },
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val section = provider.section()

        assertTrue(section.text.contains("kapot"))
    }

    @Test
    fun `shortSummary is altijd null`() {
        val provider = BeachCycleSectionProvider(
            windForecastClient = StubWindForecastClient(speedKn = 10.0, directionDeg = 270.0),
            weatherClient = DryWeatherClient(),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        assertNull(provider.shortSummary())
    }
}
