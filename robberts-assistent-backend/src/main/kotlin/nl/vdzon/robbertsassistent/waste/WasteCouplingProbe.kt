package nl.vdzon.robbertsassistent.waste

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de afvalkalender (HVC Groep, keyless — altijd echt). */
@Component
class WasteCouplingProbe(private val wasteClient: WasteClient) : CouplingProbe {

    override val id = "waste"
    override val name = "Afvalkalender"
    override val description = "Afvalophaaldata voor Robberts huisadres (HVC Groep)."
    override val configured = true
    override val mode = "echt"

    override fun test(): Pair<Boolean, String> {
        val schedule = wasteClient.upcomingPickups()
        return schedule.error?.let { false to it } ?: (true to "${schedule.pickups.size} ophaalmoment(en) gevonden")
    }
}
