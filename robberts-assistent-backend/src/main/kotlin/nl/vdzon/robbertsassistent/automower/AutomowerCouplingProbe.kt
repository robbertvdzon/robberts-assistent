package nl.vdzon.robbertsassistent.automower

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de robotmaaier (Husqvarna Automower Connect API). */
@Component
class AutomowerCouplingProbe(private val secrets: AppSecrets, private val automowerClient: AutomowerClient) : CouplingProbe {

    private val husqvarnaConfigured: Boolean
        get() = !secrets.husqvarnaAppKey.isNullOrBlank() && !secrets.husqvarnaAppSecret.isNullOrBlank()

    override val id = "automower"
    override val name = "Robotmaaier"
    override val description = "Status + starten/parkeren van de Automower (Husqvarna)."
    override val configured: Boolean get() = husqvarnaConfigured
    override val mode: String get() = if (husqvarnaConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!husqvarnaConfigured) return false to "niet geconfigureerd (stub)"
        val result = automowerClient.status()
        return result.error?.let { false to it } ?: (true to "${result.mowers.size} maaier(s) gevonden")
    }
}
