package nl.vdzon.robbertsassistent.airquality

import java.time.Duration
import java.time.Instant

/**
 * Vaste, deterministische luchtkwaliteit (AQI 25 "redelijk", UV 4, wat graspollen) — puur voor
 * tests, zodat `AirQualityTools` zonder netwerk-call getest kan worden (zelfde patroon als
 * `StubCalendarClient`). Niet als Spring-bean geregistreerd: [OpenMeteoAirQualityClient] is
 * keyless en dus altijd actief.
 */
class StubAirQualityClient : AirQualityClient {
    override fun hourlyForecast(hours: Int): AirQualityForecast {
        val now = Instant.now()
        val stubHours = (0 until hours).map { offset ->
            HourlyAirQuality(
                time = now.plus(Duration.ofHours(offset.toLong())),
                europeanAqi = 25,
                pm10 = 15.0,
                pm25 = 8.0,
                uvIndex = 4.0,
                birchPollen = 0.0,
                grassPollen = 12.0,
                ragweedPollen = 0.0,
            )
        }
        return AirQualityForecast(stubHours)
    }
}
