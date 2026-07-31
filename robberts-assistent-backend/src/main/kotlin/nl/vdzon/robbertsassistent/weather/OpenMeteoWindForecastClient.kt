package nl.vdzon.robbertsassistent.weather

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Echte windvoorspelling via de Open-Meteo forecast-API (gratis, geen API-key), zelfde bron als
 * `assistant.ai.WindTools` maar dan gestructureerd (kn + graden i.p.v. platte tekst). Coördinaten:
 * Wijk aan Zee-strand, relevant voor de aanlandige-wind-check in de kite-briefingsectie.
 *
 * Het ophalen zelf (TTL-cache, retry bij een tijdelijke fout en last-known-good) zit in
 * [ForecastFetcher]; deze klasse doet alleen de parse en het afkappen op `hours`.
 */
@Component
class OpenMeteoWindForecastClient(
    httpClient: HttpClient = HttpClient.newHttpClient(),
    now: () -> Instant = { Instant.now() },
    sleeper: (Long) -> Unit = { Thread.sleep(it) },
    retryDelaysMs: List<Long> = ForecastFetcher.DEFAULT_RETRY_DELAYS_MS,
) : WindForecastClient {

    private val objectMapper = jacksonObjectMapper()

    private val fetcher = ForecastFetcher(
        httpClient = httpClient,
        url = FORECAST_URL,
        logger = LoggerFactory.getLogger(OpenMeteoWindForecastClient::class.java),
        statusError = { status -> "Kon Open-Meteo-wind niet ophalen (HTTP $status)." },
        exceptionError = { e -> "Kon Open-Meteo-wind niet ophalen: ${e.message}" },
        now = now,
        sleeper = sleeper,
        retryDelaysMs = retryDelaysMs,
    )

    override fun hourlyForecast(hours: Int): WindForecast =
        when (val result = fetcher.fetch()) {
            is ForecastFetcher.Result.Failure -> WindForecast(emptyList(), result.message)
            is ForecastFetcher.Result.Body -> runCatching {
                val parsed = parseForecast(objectMapper.readTree(result.body))
                parsed.copy(
                    hours = parsed.hours.take(hours),
                    fetchedAt = result.fetchedAt,
                    stale = result.stale && parsed.error == null,
                )
            }.getOrElse { WindForecast(emptyList(), "Kon Open-Meteo-wind niet ophalen: ${it.message}") }
        }

    internal companion object {
        // Wijk aan Zee (strand). Gratis, geen API-key nodig.
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=52.4939&longitude=4.5992" +
            "&hourly=wind_speed_10m,wind_direction_10m" +
            "&forecast_days=7&wind_speed_unit=kn&timezone=Europe%2FAmsterdam"

        internal fun parseForecast(root: JsonNode): WindForecast {
            val hourly = root.path("hourly")
            val times = hourly.path("time").map { it.asText() }
            val speeds = hourly.path("wind_speed_10m").map { it.asDouble() }
            val dirs = hourly.path("wind_direction_10m").map { it.asDouble() }
            if (times.isEmpty()) return WindForecast(emptyList(), "Open-Meteo gaf geen windvoorspellingsdata terug.")

            val now = LocalDateTime.now(ZoneId.of("Europe/Amsterdam"))
            val hours = times.indices
                .filter { i -> LocalDateTime.parse(times[i]) >= now.withMinute(0).withSecond(0).withNano(0) }
                .map { i ->
                    HourlyWind(
                        time = LocalDateTime.parse(times[i]).atZone(ZoneId.of("Europe/Amsterdam")).toInstant(),
                        speedKn = speeds[i],
                        directionDeg = dirs[i],
                    )
                }
            return WindForecast(hours)
        }
    }
}
