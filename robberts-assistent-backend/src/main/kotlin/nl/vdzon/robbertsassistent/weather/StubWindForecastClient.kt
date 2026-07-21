package nl.vdzon.robbertsassistent.weather

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Vaste, deterministische windvoorspelling (24 kn, uit het noordwesten — aanlandig bij Wijk aan
 * Zee) — puur voor tests. Niet als Spring-bean geregistreerd: [OpenMeteoWindForecastClient] is
 * keyless en dus altijd actief.
 */
class StubWindForecastClient(
    private val speedKn: Double = 24.0,
    private val directionDeg: Double = 315.0,
) : WindForecastClient {
    override fun hourlyForecast(hours: Int): WindForecast {
        val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val stubHours = (0 until hours).map { offset ->
            HourlyWind(time = now.plus(Duration.ofHours(offset.toLong())), speedKn = speedKn, directionDeg = directionDeg)
        }
        return WindForecast(stubHours)
    }
}
