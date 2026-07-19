package nl.vdzon.robbertsassistent.weather

import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de regen-/weersvoorspelling (Open-Meteo, keyless — altijd echt). */
@Component
class WeatherCouplingProbe(private val weatherClient: WeatherClient) : CouplingProbe {

    override val id = "weather"
    override val name = "Weer/regen"
    override val description = "Regen-/weersvoorspelling bij de moestuin (Open-Meteo)."
    override val configured = true
    override val mode = "echt"

    override fun test(): Pair<Boolean, String> {
        val forecast = weatherClient.hourlyForecast(1)
        return forecast.error?.let { false to it } ?: (true to "voorspelling opgehaald (${forecast.hours.size} uur)")
    }
}
