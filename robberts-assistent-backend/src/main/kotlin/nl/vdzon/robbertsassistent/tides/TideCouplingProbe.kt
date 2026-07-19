package nl.vdzon.robbertsassistent.tides

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de getijvoorspelling (Rijkswaterstaat, keyless — altijd echt). */
@Component
class TideCouplingProbe(private val tideClient: TideClient) : CouplingProbe {

    override val id = "tides"
    override val name = "Getijden"
    override val description = "Hoog-/laagwater en waterhoogte bij IJmuiden (Rijkswaterstaat)."
    override val configured = true
    override val mode = "echt"

    override fun test(): Pair<Boolean, String> {
        val forecast = tideClient.forecast(1)
        return forecast.error?.let { false to it } ?: (true to "getijdata opgehaald (${forecast.levels.size} punt(en))")
    }
}
