package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.google.CalendarEvent
import nl.vdzon.robbertsassistent.tides.StubTideClient
import nl.vdzon.robbertsassistent.tides.TideExtreme
import nl.vdzon.robbertsassistent.tides.TideForecast
import nl.vdzon.robbertsassistent.tides.TideType
import nl.vdzon.robbertsassistent.weather.StubWeatherClient
import nl.vdzon.robbertsassistent.weather.StubWindForecastClient
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WeatherForecast
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KiteSectionProviderTest {

    private class EmptyCalendarClient : CalendarClient {
        override fun upcoming(maxResults: Int) = emptyList<CalendarEvent>()
        override fun search(query: String) = emptyList<CalendarEvent>()
        override fun eventsInRange(from: Instant, to: Instant) = emptyList<CalendarEvent>()
    }

    private class DryWeatherClient : WeatherClient {
        override fun hourlyForecast(hours: Int): WeatherForecast {
            val now = Instant.now()
            return WeatherForecast((0 until hours).map { offset ->
                nl.vdzon.robbertsassistent.weather.HourlyWeather(
                    time = now.plus(Duration.ofHours(offset.toLong())),
                    temperatureC = 15.0,
                    precipitationMm = 0.0,
                    precipitationProbabilityPct = 0,
                    weatherCode = 0,
                )
            })
        }
    }

    // --- Pure functies ---

    @Test
    fun `assessKite is groen bij ideale aanlandige wind en droog`() {
        assertEquals(RatingColor.GREEN, KiteSectionProvider.assessKite(25.0, 270.0, 0.0))
    }

    @Test
    fun `assessKite is geel bij grenswaarde windkracht`() {
        assertEquals(RatingColor.YELLOW, KiteSectionProvider.assessKite(17.0, 270.0, 0.0))
        assertEquals(RatingColor.YELLOW, KiteSectionProvider.assessKite(40.0, 270.0, 0.0))
    }

    @Test
    fun `assessKite is rood bij niet-aanlandige wind`() {
        assertEquals(RatingColor.RED, KiteSectionProvider.assessKite(25.0, 90.0, 0.0))
    }

    @Test
    fun `assessKite is rood bij neerslag ongeacht wind`() {
        assertEquals(RatingColor.RED, KiteSectionProvider.assessKite(25.0, 270.0, 1.0))
    }

    @Test
    fun `assessBeachCycle is groen bij laag water, weinig wind en droog`() {
        assertEquals(RatingColor.GREEN, KiteSectionProvider.assessBeachCycle(10.0, 0.0, true))
    }

    @Test
    fun `assessBeachCycle is geel als het niet rond laagwater is`() {
        assertEquals(RatingColor.YELLOW, KiteSectionProvider.assessBeachCycle(10.0, 0.0, false))
    }

    @Test
    fun `assessBeachCycle is rood bij te veel wind of neerslag`() {
        assertEquals(RatingColor.RED, KiteSectionProvider.assessBeachCycle(20.0, 0.0, true))
        assertEquals(RatingColor.RED, KiteSectionProvider.assessBeachCycle(10.0, 1.0, true))
    }

    @Test
    fun `isNearLowTide is waar binnen het venster rond een laagwatermoment`() {
        val now = Instant.now()
        val forecast = TideForecast(
            levels = emptyList(),
            extremes = listOf(TideExtreme(now.plus(Duration.ofHours(1)), -80, TideType.LAAGWATER)),
        )
        assertTrue(KiteSectionProvider.isNearLowTide(now, forecast))
        assertFalse(KiteSectionProvider.isNearLowTide(now.plus(Duration.ofHours(6)), forecast))
    }

    @Test
    fun `compassPoint zet graden om naar kompaspunten`() {
        assertEquals("N", KiteSectionProvider.compassPoint(0.0))
        assertEquals("W", KiteSectionProvider.compassPoint(270.0))
        assertEquals("NW", KiteSectionProvider.compassPoint(315.0))
    }

    // --- section()/shortSummary() ---

    @Test
    fun `section bouwt tekst op basis van de gestubde bronnen`() {
        val provider = KiteSectionProvider(
            windForecastClient = StubWindForecastClient(speedKn = 24.0, directionDeg = 270.0),
            weatherClient = DryWeatherClient(),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val section = provider.section()

        assertEquals("kite", section.key)
        assertTrue(section.text.contains("kiten"))
        assertTrue(section.text.contains("24 kn"))
    }

    @Test
    fun `shortSummary geeft een compacte one-liner terug`() {
        val provider = KiteSectionProvider(
            windForecastClient = StubWindForecastClient(speedKn = 24.0, directionDeg = 270.0),
            weatherClient = DryWeatherClient(),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val summary = provider.shortSummary()

        assertTrue(summary != null && summary.contains("24kn"))
    }

    @Test
    fun `section meldt een fout als een bron faalt`() {
        val provider = KiteSectionProvider(
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
}
