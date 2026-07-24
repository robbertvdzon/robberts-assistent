package nl.vdzon.robbertsassistent.zonneplan

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/**
 * Koppelingsstatus voor de zonnepanelen (Zonneplan via Home Assistant). In tegenstelling tot de
 * andere probes bevat [test] ook een domein-oordeel: zo goed als geen opbrengst gisteren
 * ([BROKEN_THRESHOLD_KWH]) wijst op een storing (kapotte omvormer, verbindingsprobleem) in plaats
 * van op een netwerk-/config-fout, en meldt dat dus expliciet als "niet ok".
 */
@Component
class ZonneplanCouplingProbe(
    private val secrets: AppSecrets,
    private val zonneplanClient: ZonneplanClient,
) : CouplingProbe {

    private val homeAssistantConfigured: Boolean
        get() = !secrets.homeAssistantUrl.isNullOrBlank() && !secrets.homeAssistantToken.isNullOrBlank()

    override val id = "zonneplan"
    override val name = "Zonnepanelen"
    override val description = "Huidig vermogen (ter info) + dagopbrengst van gisteren (Zonneplan via Home Assistant); " +
        "nagenoeg geen opbrengst gisteren wijst op een storing."
    override val configured: Boolean get() = homeAssistantConfigured
    override val mode: String get() = if (homeAssistantConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!homeAssistantConfigured) return false to "niet geconfigureerd (stub)"
        val result = zonneplanClient.status()
        result.error?.let { return false to it }

        val currentText = result.currentPowerWatt?.let { "$it W" } ?: "onbekend"
        val yesterday = result.yesterdayYieldKwh
        if (yesterday != null && yesterday < BROKEN_THRESHOLD_KWH) {
            return false to "gisteren nauwelijks opbrengst ($yesterday kWh) — mogelijk storing (huidig vermogen $currentText)"
        }
        val yesterdayText = yesterday?.let { "$it kWh" } ?: "onbekend"
        return true to "huidig vermogen $currentText, gisteren $yesterdayText opgewekt"
    }

    private companion object {
        /** Onder deze dagopbrengst (kWh) gaan we ervan uit dat de omvormer/koppeling kapot is. */
        const val BROKEN_THRESHOLD_KWH = 0.1
    }
}
