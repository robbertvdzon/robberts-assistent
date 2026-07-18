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

    override fun upcoming(maxResults: Int): List<CalendarEvent> = fetchEvents(query = null, maxResults = maxResults)

    override fun search(query: String): List<CalendarEvent> = fetchEvents(query = query, maxResults = 50)

    private fun fetchEvents(query: String?, maxResults: Int): List<CalendarEvent> {
        val params = buildList {
            add("singleEvents=true")
            add("orderBy=startTime")
            add("maxResults=$maxResults")
            add("timeMin=" + encode(Instant.now().toString()))
            if (!query.isNullOrBlank()) add("q=" + encode(query))
        }.joinToString("&")

        val request = HttpRequest.newBuilder(URI.create("$EVENTS_URL?$params"))
            .header("Authorization", "Bearer ${oauth.accessToken()}")
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Google Calendar-call faalde (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readTree(response.body()).path("items").mapNotNull { it.toCalendarEvent() }
    }

    private fun JsonNode.toCalendarEvent(): CalendarEvent? {
        val start = parseTime(path("start")) ?: return null
        val end = parseTime(path("end")) ?: start
        val summary = path("summary").asText("(geen titel)")
        val location = get("location")?.takeIf { !it.isNull }?.asText()?.takeIf { it.isNotBlank() }
        return CalendarEvent(summary = summary, start = start, end = end, location = location)
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

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        // 'primary' = de hoofdagenda van de ingelogde gebruiker.
        const val EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events"
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
