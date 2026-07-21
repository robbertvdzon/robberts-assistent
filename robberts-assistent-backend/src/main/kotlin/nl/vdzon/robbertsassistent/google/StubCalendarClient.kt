package nl.vdzon.robbertsassistent.google

import java.time.Duration
import java.time.Instant

/**
 * Stub-agenda met een paar vaste afspraken (relatief aan nu), zodat de hele keten — inclusief de
 * AI-tools — getest kan worden vóór de echte Google Calendar-koppeling er is. [GoogleClientsConfig]
 * kiest tussen deze en [GoogleCalendarClient].
 */
class StubCalendarClient : CalendarClient {
    override fun upcoming(maxResults: Int): List<CalendarEvent> {
        val now = Instant.now()
        return listOf(
            CalendarEvent(
                summary = "Standup",
                start = now.plus(Duration.ofDays(1)),
                end = now.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(15)),
                location = "Online",
            ),
            CalendarEvent(
                summary = "Tandarts controle",
                start = now.plus(Duration.ofDays(3)),
                end = now.plus(Duration.ofDays(3)).plus(Duration.ofMinutes(30)),
                location = "Tandartspraktijk Centrum",
            ),
            CalendarEvent(
                summary = "Vakantie Frankrijk",
                start = now.plus(Duration.ofDays(30)),
                end = now.plus(Duration.ofDays(44)),
                location = "Frankrijk",
                allDay = true,
            ),
        ).sortedBy { it.start }.take(maxResults)
    }

    override fun search(query: String): List<CalendarEvent> =
        upcoming(50).filter {
            it.summary.contains(query, ignoreCase = true) ||
                (it.location?.contains(query, ignoreCase = true) ?: false)
        }

    override fun eventsInRange(from: Instant, to: Instant): List<CalendarEvent> =
        upcoming(50).filter { !it.start.isBefore(from) && it.start.isBefore(to) }.sortedBy { it.start }
}
