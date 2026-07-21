package nl.vdzon.robbertsassistent.weather

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de gestructureerde windvoorspelling bij Wijk aan Zee (Open-Meteo, keyless — altijd echt). */
@Component
class WindForecastCouplingProbe(private val windForecastClient: WindForecastClient) : CouplingProbe {

    override val id = "wind-forecast"
    override val name = "Wind (kite-briefing)"
    override val description = "Gestructureerde windvoorspelling bij Wijk aan Zee (Open-Meteo) voor de Morgen-briefing."
    override val configured = true
    override val mode = "echt"

    override fun test(): Pair<Boolean, String> {
        val forecast = windForecastClient.hourlyForecast(1)
        return forecast.error?.let { false to it } ?: (true to "voorspelling opgehaald (${forecast.hours.size} uur)")
    }
}
