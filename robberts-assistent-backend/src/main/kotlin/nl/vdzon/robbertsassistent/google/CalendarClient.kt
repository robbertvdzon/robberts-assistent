package nl.vdzon.robbertsassistent.google

import java.time.Instant

/** Eén agenda-afspraak, leverancier-onafhankelijk. */
data class CalendarEvent(
    val summary: String,
    val start: Instant,
    val end: Instant,
    val location: String? = null,
    // Hele-dag-item (Google Agenda "date" i.p.v. "dateTime") — bv. vakantiedagen. Gaat verloren
    // als je alleen op start/end filtert, dus expliciet bewaard (zie briefing.KiteSectionProvider).
    val allDay: Boolean = false,
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

    /**
     * Afspraken over ALLE agenda's van de gebruiker (niet alleen de primaire) binnen [from]..[to],
     * oplopend op starttijd — gebruikt door de "Morgen"-briefing (agenda-sectie: komende 7 dagen,
     * en de kite-sectie: hele-dag-items voor vakantiedetectie).
     */
    fun eventsInRange(from: Instant, to: Instant): List<CalendarEvent>
}
