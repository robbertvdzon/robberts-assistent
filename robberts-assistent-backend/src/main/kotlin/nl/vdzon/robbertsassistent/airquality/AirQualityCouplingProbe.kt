package nl.vdzon.robbertsassistent.airquality

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor luchtkwaliteit/UV/pollen (Open-Meteo, keyless — altijd echt). */
@Component
class AirQualityCouplingProbe(private val airQualityClient: AirQualityClient) : CouplingProbe {

    override val id = "airquality"
    override val name = "Luchtkwaliteit/UV/pollen"
    override val description = "Luchtkwaliteitsindex, UV-index en pollen bij de moestuin (Open-Meteo)."
    override val configured = true
    override val mode = "echt"

    override fun test(): Pair<Boolean, String> {
        val forecast = airQualityClient.hourlyForecast(1)
        return forecast.error?.let { false to it } ?: (true to "luchtkwaliteitsdata opgehaald (${forecast.hours.size} uur)")
    }
}
