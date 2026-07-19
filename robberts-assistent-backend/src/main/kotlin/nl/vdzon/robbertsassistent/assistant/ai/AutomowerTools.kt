package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.automower.AutomowerClient
import nl.vdzon.robbertsassistent.automower.MowerStatus
import nl.vdzon.robbertsassistent.automower.activityDescription
import nl.vdzon.robbertsassistent.automower.stateDescription
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent toegang tot de robotmaaier (Husqvarna Automower) via [AutomowerClient]
 * (client_credentials-app-key/secret, zie CLAUDE.md §5). In tegenstelling tot de andere
 * skill-tools stuurt dit ook een fysiek apparaat aan (starten/parkeren) — gebruik de actie-tools
 * alleen als daar expliciet om gevraagd wordt.
 */
@Component
class AutomowerTools(private val automowerClient: AutomowerClient) {

    @Tool(
        description = "Haal de status van de robotmaaier (Husqvarna Automower) op: naam, model, " +
            "activiteit (maait/laadt op/geparkeerd), status en batterijniveau.",
    )
    fun getMowerStatus(): String {
        val result = automowerClient.status()
        result.error?.let { return it }
        if (result.mowers.isEmpty()) return "Geen maaier gevonden op dit account."
        return result.mowers.joinToString("\n") { line(it) }
    }

    @Tool(
        description = "Start de robotmaaier voor het opgegeven aantal minuten, los van het normale " +
            "maaischema. Gebruik dit alleen als er expliciet om gevraagd wordt, bv. 'laat de maaier " +
            "nu 30 minuten maaien'.",
    )
    fun startMowing(durationMinutes: Int): String {
        val result = automowerClient.startMowing(durationMinutes)
        return if (result.ok) "Maaier gestart voor $durationMinutes minuten." else result.error ?: "Kon de maaier niet starten."
    }

    @Tool(
        description = "Stuur de robotmaaier terug naar het laadstation; hij hervat vanzelf het " +
            "normale schema op het volgende geplande moment. Gebruik dit voor 'zet de maaier terug " +
            "naar binnen/het laadstation' of 'stop met maaien'.",
    )
    fun parkMower(): String {
        val result = automowerClient.park()
        return if (result.ok) "Maaier gaat terug naar het laadstation." else result.error ?: "Kon de maaier niet terugsturen."
    }

    private fun line(mower: MowerStatus): String {
        val battery = mower.batteryPercent?.let { ", batterij $it%" } ?: ""
        val error = if (mower.errorCode != 0) ", let op: foutcode ${mower.errorCode}" else ""
        val connected = if (!mower.connected) ", niet verbonden" else ""
        return "${mower.name} (${mower.model}): ${activityDescription(mower.activity)}, " +
            "${stateDescription(mower.state)}$battery$error$connected"
    }
}
