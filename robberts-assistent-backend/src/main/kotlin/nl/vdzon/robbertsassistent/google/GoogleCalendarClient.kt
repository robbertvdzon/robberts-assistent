package nl.vdzon.robbertsassistent.google

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Leest Robberts primaire Google-agenda via de Calendar REST-API (v3), met een access-token uit
 * [GoogleOAuthService]. Bewust rauwe HTTP + Jackson (zoals WindTools) i.p.v. de zware
 * google-api-services-calendar-library.
 *
 * NB: nog niet end-to-end geverifieerd (geen echte OAuth-token beschikbaar) — zie
 * docs/foundation-couplings.md, fase 3.
 */
class GoogleCalendarClient(
    private val oauth: GoogleOAuthService,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : CalendarClient {
    private val objectMapper = jacksonObjectMapper()

    override fun upcoming(maxResults: Int): List<CalendarEvent> =
        fetchEvents(PRIMARY_CALENDAR_ID, query = null, timeMin = Instant.now(), timeMax = null, maxResults = maxResults)

    override fun search(query: String): List<CalendarEvent> =
        fetchEvents(PRIMARY_CALENDAR_ID, query = query, timeMin = Instant.now(), timeMax = null, maxResults = 50)

    override fun eventsInRange(from: Instant, to: Instant): List<CalendarEvent> =
        calendarIds().flatMap { calendarId ->
            fetchEvents(calendarId, query = null, timeMin = from, timeMax = to, maxResults = 250)
        }.sortedBy { it.start }

    /** Alle agenda's van de ingelogde gebruiker (Calendar List API), inclusief de primaire. */
    private fun calendarIds(): List<String> = runCatching {
        val request = HttpRequest.newBuilder(URI.create(CALENDAR_LIST_URL))
            .header("Authorization", "Bearer ${oauth.accessToken()}")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Google CalendarList-call faalde (HTTP ${response.statusCode()}): ${response.body()}")
        }
        objectMapper.readTree(response.body()).path("items").mapNotNull { it.path("id").takeIf { id -> !id.isMissingNode }?.asText() }
    }.getOrElse { listOf(PRIMARY_CALENDAR_ID) }.ifEmpty { listOf(PRIMARY_CALENDAR_ID) }

    private fun fetchEvents(calendarId: String, query: String?, timeMin: Instant, timeMax: Instant?, maxResults: Int): List<CalendarEvent> {
        val params = buildList {
            add("singleEvents=true")
            add("orderBy=startTime")
            add("maxResults=$maxResults")
            add("timeMin=" + encode(timeMin.toString()))
            if (timeMax != null) add("timeMax=" + encode(timeMax.toString()))
            if (!query.isNullOrBlank()) add("q=" + encode(query))
        }.joinToString("&")

        val url = "https://www.googleapis.com/calendar/v3/calendars/${encode(calendarId)}/events"
        val request = HttpRequest.newBuilder(URI.create("$url?$params"))
            .header("Authorization", "Bearer ${oauth.accessToken()}")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Google Calendar-call faalde (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readTree(response.body()).path("items").mapNotNull { toCalendarEvent(it) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    internal companion object {
        // 'primary' = de hoofdagenda van de ingelogde gebruiker.
        const val PRIMARY_CALENDAR_ID = "primary"
        const val CALENDAR_LIST_URL = "https://www.googleapis.com/calendar/v3/users/me/calendarList"
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        internal fun toCalendarEvent(node: JsonNode): CalendarEvent? {
            val startNode = node.path("start")
            val start = parseTime(startNode) ?: return null
            val end = parseTime(node.path("end")) ?: start
            val summary = node.path("summary").asText("(geen titel)")
            val location = node.get("location")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
            val allDay = startNode.get("date") != null && startNode.get("dateTime") == null
            return CalendarEvent(summary = summary, start = start, end = end, location = location, allDay = allDay)
        }

        private fun parseTime(node: JsonNode): Instant? {
            node.get("dateTime")?.takeIf { !it.isNull }?.asText()?.let {
                return runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
            }
            node.get("date")?.takeIf { !it.isNull }?.asText()?.let {
                return runCatching { LocalDate.parse(it).atStartOfDay(ZONE).toInstant() }.getOrNull()
            }
            return null
        }
    }
}
