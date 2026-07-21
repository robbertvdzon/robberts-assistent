package nl.vdzon.robbertsassistent.weather

import java.time.Instant

/** Eén uur uit de windvoorspelling: snelheid in knopen, richting waar de wind VANDAAN komt (0-360). */
data class HourlyWind(
    val time: Instant,
    val speedKn: Double,
    val directionDeg: Double,
)

/**
 * Resultaat van een windvoorspelling-ophaal-poging. Bij een netwerk-/serverfout is [hours] leeg en
 * [error] gezet, zelfde patroon als [WeatherForecast]/`TideForecast`.
 */
data class WindForecast(
    val hours: List<HourlyWind>,
    val error: String? = null,
)

/**
 * Gestructureerde windvoorspelling (snelheid + richting) bij Wijk aan Zee, voor de kite-/
 * strandfiets-briefingsectie. De bestaande `assistant.ai.WindTools` levert alleen platte,
 * AI-geschikte tekst (windfinder-scrape) — die is niet herbruikbaar als betrouwbare, niet-AI-
 * afhankelijke databron voor code die zelf een 🟢/🟡/🔴-beoordeling moet maken. Fase 0 (keyless,
 * geen secret nodig): [OpenMeteoWindForecastClient] is de enige, altijd-actieve implementatie.
 * [StubWindForecastClient] bestaat alleen voor tests.
 */
interface WindForecastClient {
    /** Uurvoorspelling vanaf nu, oplopend in tijd, voor maximaal [hours] uur vooruit (max. 168). */
    fun hourlyForecast(hours: Int = 48): WindForecast
}
