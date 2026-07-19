package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.waste.WasteClient
import nl.vdzon.robbertsassistent.waste.WastePickup
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Geeft de chat-assistent toegang tot de afvalophaalkalender van Robberts huisadres via
 * [WasteClient] (HVC Groep, keyless — geen secret nodig, zie CLAUDE.md §5).
 */
@Component
class WasteTools(private val wasteClient: WasteClient) {

    @Tool(
        description = "Haal de eerstvolgende afvalophaaldata op (gft, plastic/blik/drinkpakken, " +
            "papier, restafval) voor Robberts huisadres. Gebruik dit voor vragen als 'wanneer moet " +
            "de gft-bak buiten' of 'welke bak moet er morgen buiten'.",
    )
    fun getWastePickups(): String {
        val schedule = wasteClient.upcomingPickups()
        schedule.error?.let { return it }
        if (schedule.pickups.isEmpty()) return "Geen afvalophaaldata gevonden."
        return schedule.pickups.joinToString("\n") { line(it) }
    }

    private fun line(pickup: WastePickup): String = "${DATE_FORMATTER.format(pickup.date)}: ${pickup.type}"

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)", Locale.forLanguageTag("nl"))
    }
}
