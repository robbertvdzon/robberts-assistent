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
import nl.vdzon.robbertsassistent.weather.WindForecast
import nl.vdzon.robbertsassistent.weather.WindForecastClient
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    private class CountingWindClient : WindForecastClient {
        var calls = 0
        private val delegate = StubWindForecastClient(speedKn = 24.0, directionDeg = 270.0)

        override fun hourlyForecast(hours: Int): WindForecast {
            calls++
            return delegate.hourlyForecast(hours)
        }
    }

    /** Wind uit de last-known-good: zelfde data, maar met ophaalmoment en verouderd-markering. */
    private class StaleWindClient(private val fetchedAt: Instant) : WindForecastClient {
        private val delegate = StubWindForecastClient(speedKn = 24.0, directionDeg = 270.0)
        override fun hourlyForecast(hours: Int): WindForecast =
            delegate.hourlyForecast(hours).copy(fetchedAt = fetchedAt, stale = true)
    }

    private class StaleWeatherClient(private val fetchedAt: Instant) : WeatherClient {
        override fun hourlyForecast(hours: Int): WeatherForecast =
            DryWeatherClient().hourlyForecast(hours).copy(fetchedAt = fetchedAt, stale = true)
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

    @Test
    fun `beste kiteslot kiest groen boven geel en rood en bij gelijkstand het vroegste`() {
        val red = slot(label = "Ochtend", kite = RatingColor.RED)
        val firstGreen = slot(label = "Middag", kite = RatingColor.GREEN)
        val secondGreen = slot(label = "Avond", kite = RatingColor.GREEN)

        assertEquals(firstGreen, bestSlot(listOf(red, firstGreen, secondGreen)) { it.kite })
    }

    // --- section()/shortSummary() ---

    @Test
    fun `section bouwt tekst op basis van de gestubde bronnen`() {
        val windClient = CountingWindClient()
        val provider = KiteSectionProvider(
            windForecastClient = windClient,
            weatherClient = DryWeatherClient(),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val section = provider.section()

        assertEquals("kite", section.key)
        assertEquals("Kiten", section.title)
        assertTrue(section.text.contains("24 kn"))
        assertEquals(BriefingStatus.GOED, section.status)
        assertEquals("24 kn W", section.tileLabel)
        assertEquals(1, windClient.calls, "sectietekst en tegel gebruiken dezelfde beoordeling")
        assertFalse(section.text.contains("gegevens van"), "verse data krijgt geen verouderd-melding")
    }

    @Test
    fun `section meldt verouderde data met het oudste ophaalmoment van wind en weer`() {
        // 06:05Z = 08:05 in Europe/Amsterdam (zomertijd), 07:05Z = 09:05 — het oudste telt.
        val provider = KiteSectionProvider(
            windForecastClient = StaleWindClient(Instant.parse("2026-07-31T06:05:00Z")),
            weatherClient = StaleWeatherClient(Instant.parse("2026-07-31T07:05:00Z")),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        val section = provider.section()

        assertTrue(section.text.contains("24 kn"), "de normale inhoud blijft staan")
        assertTrue(section.text.contains("(gegevens van 08:05)"), "werkelijke tekst: ${section.text}")
        assertEquals(BriefingStatus.GOED, section.status, "verouderde data levert gewoon een tegel op")
    }

    @Test
    fun `section meldt geen verouderde data als alleen het ophaalmoment bekend is`() {
        val provider = KiteSectionProvider(
            windForecastClient = object : WindForecastClient {
                override fun hourlyForecast(hours: Int) =
                    StubWindForecastClient(speedKn = 24.0, directionDeg = 270.0)
                        .hourlyForecast(hours)
                        .copy(fetchedAt = Instant.parse("2026-07-31T06:05:00Z"))
            },
            weatherClient = DryWeatherClient(),
            tideClient = StubTideClient(),
            calendarClient = EmptyCalendarClient(),
        )

        assertFalse(provider.section().text.contains("gegevens van"))
    }

    // --- 18:00-push: alleen op vrije dagen en alleen als het kan ---

    private val zone = java.time.ZoneId.of("Europe/Amsterdam")
    private val friday = java.time.LocalDate.parse("2026-11-27")

    private fun windOn(day: java.time.LocalDate, speed: (Int) -> Double, direction: Double = 270.0) = WindForecast(
        (0..23).map { nl.vdzon.robbertsassistent.weather.HourlyWind(day.atTime(it, 0).atZone(zone).toInstant(), speed(it), direction) },
    )

    private fun weatherOn(day: java.time.LocalDate, rain: (Int) -> Double = { 0.0 }) = WeatherForecast(
        (0..23).map { nl.vdzon.robbertsassistent.weather.HourlyWeather(day.atTime(it, 0).atZone(zone).toInstant(), 10.0, rain(it), 0, 0) },
    )

    private fun fixed(wind: WindForecast, weather: WeatherForecast, today: String) = KiteSectionProvider(
        windForecastClient = object : WindForecastClient { override fun hourlyForecast(hours: Int) = wind },
        weatherClient = object : WeatherClient { override fun hourlyForecast(hours: Int) = weather },
        tideClient = StubTideClient(),
        calendarClient = EmptyCalendarClient(),
        clock = java.time.Clock.fixed(Instant.parse("${today}T17:00:00Z"), java.time.ZoneOffset.UTC),
    )

    @Test
    fun `push noemt kiten op een vrije dag met het stuk van de dag waarop het kan`() {
        val wind = windOn(friday, { if (it in 11..14) 24.0 else 8.0 })

        assertEquals("kiten kan 11–15 uur (24 kn W)", fixed(wind, weatherOn(friday), "2026-11-26").shortSummary())
    }

    @Test
    fun `push zwijgt over kiten als het niet kan of als morgen geen vrije dag is`() {
        val good = windOn(friday, { 24.0 })
        assertNull(fixed(windOn(friday, { 8.0 }), weatherOn(friday), "2026-11-26").shortSummary())
        assertNull(fixed(good, weatherOn(friday, { 2.0 }), "2026-11-26").shortSummary())
        assertNull(fixed(windOn(friday, { 24.0 }, direction = 90.0), weatherOn(friday), "2026-11-26").shortSummary())
        // Maandag: morgen is dinsdag, ook al waait het dan goed.
        val tuesday = java.time.LocalDate.parse("2026-11-24")
        assertNull(fixed(windOn(tuesday, { 24.0 }), weatherOn(tuesday), "2026-11-23").shortSummary())
        // Eén los uur is te kort.
        assertNull(fixed(windOn(friday, { if (it == 12) 24.0 else 8.0 }), weatherOn(friday), "2026-11-26").shortSummary())
    }

    @Test
    fun `net te weinig wind is misschien, en groen gaat voor geel`() {
        assertEquals(
            "kiten kan misschien 9–18 uur (17 kn W)",
            fixed(windOn(friday, { 17.0 }), weatherOn(friday), "2026-11-26").shortSummary(),
        )
        val mixed = windOn(friday, { if (it in 13..15) 26.0 else 17.0 })
        assertEquals("kiten kan 13–16 uur (26 kn W)", fixed(mixed, weatherOn(friday), "2026-11-26").shortSummary())
    }

    @Test
    fun `strandfietsen - beste moment is drieenhalf uur voor hoogwater, alleen overdag en bij rustig droog weer`() {
        fun tide(vararg times: String) = TideForecast(
            emptyList(),
            times.map { TideExtreme(friday.atTime(java.time.LocalTime.parse(it)).atZone(zone).toInstant(), 100, TideType.HOOGWATER) } +
                TideExtreme(friday.atTime(10, 0).atZone(zone).toInstant(), -80, TideType.LAAGWATER),
        )
        val calm = windOn(friday, { 8.0 })
        val dry = weatherOn(friday)

        val moments = FreeDayOutlook.beachMoments(friday, zone, calm, dry, tide("04:10", "16:40"))
        // 04:10 − 3,5 uur valt in de nacht; alleen 13:10 blijft over.
        assertEquals("strandfietsen kan, beste moment 13:10", FreeDayOutlook.beachSummary(moments, zone))

        assertEquals(emptyList(), FreeDayOutlook.beachMoments(friday, zone, windOn(friday, { 20.0 }), dry, tide("16:40")))
        assertEquals(emptyList(), FreeDayOutlook.beachMoments(friday, zone, calm, weatherOn(friday, { if (it == 13) 1.0 else 0.0 }), tide("16:40")))
        assertEquals(emptyList(), FreeDayOutlook.beachMoments(friday.minusDays(1), zone, calm, dry, tide("16:40")))
        assertTrue(FreeDayOutlook.isFreeDay(friday) && !FreeDayOutlook.isFreeDay(friday.minusDays(1)))
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
        assertNull(section.status)
        assertNull(section.tileLabel)
    }

    private fun slot(label: String, kite: RatingColor) = SlotAssessment(
        label = label,
        windKn = 24,
        windDirection = "W",
        windText = "24 kn (W)",
        precipitationMm = 0.0,
        nearLowTide = true,
        nearestLowTideAt = null,
        kite = kite,
        beach = RatingColor.GREEN,
    )
}
