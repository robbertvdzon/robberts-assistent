package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.waste.WasteClient
import nl.vdzon.robbertsassistent.waste.WastePickup
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Afval-briefingsectie: welke afvalbak(ken) de komende 7 dagen (vandaag t/m +6 dagen) buiten
 * moeten, plus een korte "vanavond buiten"-melding voor de 18:00-push. Geen AI-call nodig — de
 * tekst is deterministisch op te bouwen uit [WastePickup]-data (zie [WasteClient]).
 */
@Component
class WasteSectionProvider internal constructor(
    private val wasteClient: WasteClient,
    private val clock: Clock,
) : BriefingSectionProvider {

    @Autowired
    constructor(wasteClient: WasteClient) : this(wasteClient, Clock.system(AMSTERDAM_ZONE))

    private val logger = LoggerFactory.getLogger(javaClass)

    override val order = 15

    override fun section(): BriefingSection {
        return runCatching { buildSection() }
            .getOrElse {
                logger.warn("Afvalkalender ophalen mislukt", it)
                BriefingSection(key = "waste", title = "Afval", text = "Kon de afvalkalender niet ophalen.")
            }
    }

    override fun shortSummary(): String? {
        val schedule = runCatching { wasteClient.upcomingPickups() }.getOrNull() ?: return null
        if (schedule.error != null) {
            return null
        }
        val tomorrow = localToday().plusDays(1)
        val types = schedule.pickups.filter { it.date == tomorrow }.map { it.type }
        if (types.isEmpty()) {
            return null
        }
        return "Zet vanavond de ${types.joinToString(" & ")} buiten"
    }

    private fun buildSection(): BriefingSection {
        val schedule = wasteClient.upcomingPickups()
        if (schedule.error != null) {
            return BriefingSection(
                key = "waste",
                title = "Afval",
                text = "Kon de afvalkalender niet ophalen: ${schedule.error}",
            )
        }
        val today = localToday()
        val until = today.plusDays(6)
        val upcoming = schedule.pickups.filter { !it.date.isBefore(today) && !it.date.isAfter(until) }
        val text = if (upcoming.isEmpty()) {
            "Geen ophaalmomenten deze week."
        } else {
            val formatter = DateTimeFormatter.ofPattern("dd-MM")
            upcoming.joinToString("\n") { "${formatter.format(it.date)}: ${it.type}" }
        }
        val firstDate = upcoming.minOfOrNull { it.date }
        val label = if (firstDate == null) {
            "geen"
        } else {
            upcoming.asSequence()
                .filter { it.date == firstDate }
                .map { shortType(it.type) }
                .distinct()
                .joinToString(" + ")
        }
        val status = if (firstDate != null && !firstDate.isAfter(today.plusDays(1))) {
            BriefingStatus.LET_OP
        } else {
            BriefingStatus.GOED
        }
        return BriefingSection(key = "waste", title = "Afval", text = text, status = status, tileLabel = label)
    }

    private fun shortType(type: String): String {
        val normalized = type.lowercase()
        return when {
            "gft" in normalized -> "gft"
            "pbd" in normalized || "plastic" in normalized || "drinkpakken" in normalized -> "pbd"
            "papier" in normalized -> "papier"
            "rest" in normalized -> "rest"
            else -> normalized
        }
    }

    private fun localToday(): LocalDate = LocalDate.now(clock.withZone(AMSTERDAM_ZONE))

    private companion object {
        val AMSTERDAM_ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}
