package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/**
 * Koppeling voor de Watches-functionaliteit. Geconfigureerd zodra AI beschikbaar is (niet mock).
 */
@Component
class WatchCouplingProbe(private val appSecrets: AppSecrets) : CouplingProbe {
    override val id = "watches"
    override val name = "Watches"
    override val description = "Langdurige zoekopdrachten met AI-beoordeling"

    override val configured: Boolean
        get() = !appSecrets.effectiveMockAi

    override val mode: String
        get() = if (configured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        return if (configured) {
            true to "AI beschikbaar voor watch-beoordeling"
        } else {
            false to "Mock-AI actief, watches krijgen status ONBEKEND"
        }
    }
}
