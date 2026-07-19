package nl.vdzon.robbertsassistent.weather

import java.time.Duration
import java.time.Instant

/**
 * Vaste, deterministische voorspelling (droog, 18°C, half bewolkt) — puur voor tests, zodat
 * `WeatherTools` zonder netwerk-call getest kan worden (zelfde patroon als `StubCalendarClient`).
 * Niet als Spring-bean geregistreerd: [OpenMeteoWeatherClient] is keyless en dus altijd actief.
 */
class StubWeatherClient : WeatherClient {
    override fun hourlyForecast(hours: Int): WeatherForecast {
        val now = Instant.now()
        val stubHours = (0 until hours).map { offset ->
            HourlyWeather(
                time = now.plus(Duration.ofHours(offset.toLong())),
                temperatureC = 18.0,
                precipitationMm = 0.0,
                precipitationProbabilityPct = 5,
                weatherCode = 2,
            )
        }
        return WeatherForecast(stubHours)
    }
}
