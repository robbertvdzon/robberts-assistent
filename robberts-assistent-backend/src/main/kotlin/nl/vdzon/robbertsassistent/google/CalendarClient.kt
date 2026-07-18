package nl.vdzon.robbertsassistent.google

import java.time.Instant

/** Eén agenda-afspraak, leverancier-onafhankelijk. */
data class CalendarEvent(
    val summary: String,
    val start: Instant,
    val end: Instant,
    val location: String? = null,
)

/**
 * Leestoegang tot Robberts agenda. Fase 0 gebruikt [StubCalendarClient]; fase 3 vervangt die door
 * een echte Google Calendar-implementatie (OAuth refresh-token) zonder de tools te raken.
 */
interface CalendarClient {
    /** Aankomende afspraken, oplopend op starttijd. */
    fun upcoming(maxResults: Int = 10): List<CalendarEvent>

    /** Aankomende afspraken waarvan de titel/locatie [query] bevat (hoofdletterongevoelig). */
    fun search(query: String): List<CalendarEvent>
}
