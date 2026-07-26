package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.waste.WasteClient
import nl.vdzon.robbertsassistent.waste.WastePickup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Afval-briefingsectie: welke afvalbak(ken) de komende 7 dagen (vandaag t/m +6 dagen) buiten
 * moeten, plus een korte "vanavond buiten"-melding voor de 18:00-push. Geen AI-call nodig — de
 * tekst is deterministisch op te bouwen uit [WastePickup]-data (zie [WasteClient]).
 */
@Component
class WasteSectionProvider(
    private val wasteClient: WasteClient,
) : BriefingSectionProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val order = 15

    override fun section(): BriefingSection {
        val text = runCatching { buildText() }
            .getOrElse {
                logger.warn("Afvalkalender ophalen mislukt", it)
                "Kon de afvalkalender niet ophalen."
            }
        return BriefingSection(key = "waste", title = "Afval", text = text)
    }

    override fun shortSummary(): String? {
        val schedule = runCatching { wasteClient.upcomingPickups() }.getOrNull() ?: return null
        if (schedule.error != null) {
            return null
        }
        val tomorrow = LocalDate.now().plusDays(1)
        val types = schedule.pickups.filter { it.date == tomorrow }.map { it.type }
        if (types.isEmpty()) {
            return null
        }
        return "Zet vanavond de ${types.joinToString(" & ")} buiten"
    }

    private fun buildText(): String {
        val schedule = wasteClient.upcomingPickups()
        if (schedule.error != null) {
            return "Kon de afvalkalender niet ophalen: ${schedule.error}"
        }
        val today = LocalDate.now()
        val until = today.plusDays(6)
        val upcoming = schedule.pickups.filter { !it.date.isBefore(today) && !it.date.isAfter(until) }
        if (upcoming.isEmpty()) {
            return "Geen ophaalmomenten deze week."
        }
        val formatter = DateTimeFormatter.ofPattern("dd-MM")
        return upcoming.joinToString("\n") { "${formatter.format(it.date)}: ${it.type}" }
    }
}
