package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.google.CalendarEvent
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent leestoegang tot Robberts agenda. Via deze tool test je de agenda-
 * koppeling met natuurlijke taal ("wanneer moet ik naar de tandarts", "wat staat er komende dagen").
 */
@Component
class CalendarTools(private val calendarClient: CalendarClient) {

    @Tool(description = "Geef Robberts aankomende agenda-afspraken (oplopend op tijd).")
    fun upcomingEvents(): String {
        val events = calendarClient.upcoming()
        if (events.isEmpty()) return "Er staan geen afspraken in de agenda."
        return events.joinToString("\n") { it.toLine() }
    }

    @Tool(
        description = "Zoek in Robberts agenda naar afspraken die een trefwoord bevatten, bv. " +
            "'tandarts' of 'vakantie'.",
    )
    fun findEvents(
        @ToolParam(description = "Het trefwoord om op te zoeken in titel/locatie") query: String,
    ): String {
        val events = calendarClient.search(query)
        if (events.isEmpty()) return "Geen afspraken gevonden voor \"$query\"."
        return events.joinToString("\n") { it.toLine() }
    }

    private fun CalendarEvent.toLine(): String {
        val where = location?.let { " @ $it" } ?: ""
        return "- $summary: ${FORMATTER.format(start)}$where"
    }

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
    }
}
