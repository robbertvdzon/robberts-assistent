package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.google.CalendarEvent
import nl.vdzon.robbertsassistent.tides.StubTideClient
import nl.vdzon.robbertsassistent.weather.FakeHttpClient
import nl.vdzon.robbertsassistent.weather.OpenMeteoWeatherClient
import nl.vdzon.robbertsassistent.weather.OpenMeteoWindForecastClient
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Borgt dat één briefing-opbouw (weerkaart + kiten + strandfietsen) dankzij de TTL-cache in de
 * Open-Meteo-clients nog maar één HTTP-call per URL doet i.p.v. drie.
 */
class WeatherCallSharingTest {

    private class EmptyCalendarClient : CalendarClient {
        override fun upcoming(maxResults: Int) = emptyList<CalendarEvent>()
        override fun search(query: String) = emptyList<CalendarEvent>()
        override fun eventsInRange(from: Instant, to: Instant) = emptyList<CalendarEvent>()
    }

    @Test
    fun `de drie weersecties delen samen één weer-call en één wind-call`() {
        val weatherHttp = FakeHttpClient(FakeHttpClient.Reply.Status(200, weatherJson()))
        val windHttp = FakeHttpClient(FakeHttpClient.Reply.Status(200, windJson()))
        val weatherClient = OpenMeteoWeatherClient(httpClient = weatherHttp, sleeper = { })
        val windClient = OpenMeteoWindForecastClient(httpClient = windHttp, sleeper = { })
        val tideClient = StubTideClient()
        val calendarClient = EmptyCalendarClient()

        val providers = listOf(
            WeatherMapSectionProvider(
                windClient,
                weatherClient,
                tideClient,
                StubCoastMapImageBuilder(),
                InMemoryWeatherMapStorage(),
            ),
            KiteSectionProvider(windClient, weatherClient, tideClient, calendarClient),
            BeachCycleSectionProvider(windClient, weatherClient, tideClient, calendarClient),
        )
        providers.forEach { it.section() }

        assertEquals(1, weatherHttp.calls)
        assertEquals(1, windHttp.calls)
    }

    private fun times(count: Int): String {
        val start = LocalDateTime.now(ZoneId.of("Europe/Amsterdam")).withMinute(0).withSecond(0).withNano(0)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        return (0 until count).joinToString(",") { "\"${formatter.format(start.plusHours(it.toLong()))}\"" }
    }

    private fun weatherJson(count: Int = 48): String = """
        {"hourly": {"time": [${times(count)}],
         "temperature_2m": [${(0 until count).joinToString(",") { "15.0" }}],
         "precipitation": [${(0 until count).joinToString(",") { "0.0" }}],
         "precipitation_probability": [${(0 until count).joinToString(",") { "0" }}],
         "weathercode": [${(0 until count).joinToString(",") { "0" }}]}}
    """.trimIndent()

    private fun windJson(count: Int = 48): String = """
        {"hourly": {"time": [${times(count)}],
         "wind_speed_10m": [${(0 until count).joinToString(",") { "24.0" }}],
         "wind_direction_10m": [${(0 until count).joinToString(",") { "270.0" }}]}}
    """.trimIndent()
}
