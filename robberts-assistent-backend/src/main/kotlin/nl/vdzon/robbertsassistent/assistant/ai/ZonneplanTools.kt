package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.zonneplan.ZonneplanClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * Geeft de chat-assistent toegang tot de zonnepanelen-status via [ZonneplanClient] (Zonneplan via
 * Home Assistant, zie CLAUDE.md §5).
 */
@Component
class ZonneplanTools(private val zonneplanClient: ZonneplanClient) {

    @Tool(
        description = "Haal de status van de zonnepanelen op: huidig omvormervermogen (W, ter info) en de " +
            "opbrengst van gisteren (kWh, via de Zonneplan-integratie in Home Assistant). Nagenoeg geen " +
            "opbrengst gisteren kan op een storing wijzen.",
    )
    fun getSolarStatus(): String {
        val result = zonneplanClient.status()
        result.error?.let { return it }
        val current = result.currentPowerWatt?.let { "$it W" } ?: "onbekend"
        val yesterday = result.yesterdayYieldKwh?.let { "$it kWh" } ?: "onbekend"
        return "Huidig vermogen: $current. Gisteren opgewekt: $yesterday."
    }
}
