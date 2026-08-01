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
 * Echte weersvoorspelling via de Open-Meteo forecast-API (open-meteo.com) — gratis, geen API-key.
 * Coördinaten: Luttik Cie 12, Heemskerk (Robberts moestuin).
 *
 * Het ophalen zelf (TTL-cache, retry bij een tijdelijke fout en last-known-good) zit in
 * [ForecastFetcher]; deze klasse doet alleen de parse en het afkappen op `hours`.
 */
@Component
class OpenMeteoWeatherClient(
    httpClient: HttpClient = HttpClient.newHttpClient(),
    now: () -> Instant = { Instant.now() },
    sleeper: (Long) -> Unit = { Thread.sleep(it) },
    retryDelaysMs: List<Long> = ForecastFetcher.DEFAULT_RETRY_DELAYS_MS,
) : WeatherClient {

    private val objectMapper = jacksonObjectMapper()

    private val fetcher = ForecastFetcher(
        httpClient = httpClient,
        url = FORECAST_URL,
        logger = LoggerFactory.getLogger(OpenMeteoWeatherClient::class.java),
        statusError = { status -> "Kon Open-Meteo niet ophalen (HTTP $status)." },
        exceptionError = { e -> "Kon Open-Meteo niet ophalen: ${e.message}" },
        now = now,
        sleeper = sleeper,
        retryDelaysMs = retryDelaysMs,
    )

    override fun hourlyForecast(hours: Int): WeatherForecast =
        when (val result = fetcher.fetch()) {
            is ForecastFetcher.Result.Failure -> WeatherForecast(emptyList(), result.message)
            is ForecastFetcher.Result.Body -> runCatching {
                val parsed = parseForecast(objectMapper.readTree(result.body))
                parsed.copy(
                    hours = parsed.hours.take(hours),
                    fetchedAt = result.fetchedAt,
                    stale = result.stale && parsed.error == null,
                )
            }.getOrElse { WeatherForecast(emptyList(), "Kon Open-Meteo niet ophalen: ${it.message}") }
        }

    internal companion object {
        // Luttik Cie 12, Heemskerk (moestuin). Gratis, geen API-key nodig.
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=52.5078&longitude=4.6420" +
            "&hourly=temperature_2m,precipitation,precipitation_probability,weathercode" +
            "&forecast_days=3&timezone=Europe%2FAmsterdam"

        /** Zet de ruwe Open-Meteo-JSON om naar een oplopende lijst [HourlyWeather], vanaf het huidige uur. */
        internal fun parseForecast(root: JsonNode): WeatherForecast {
            val hourly = root.path("hourly")
            val times = hourly.path("time").map { it.asText() }
            val temps = hourly.path("temperature_2m").map { it.asDouble() }
            val precipitation = hourly.path("precipitation").map { it.asDouble() }
            val precipitationProbability = hourly.path("precipitation_probability")
            val weatherCodes = hourly.path("weathercode").map { it.asInt() }
            if (times.isEmpty()) return WeatherForecast(emptyList(), "Open-Meteo gaf geen voorspellingsdata terug.")

            val now = LocalDateTime.now(ZoneId.of("Europe/Amsterdam"))
            val hours = times.indices
                .filter { i -> LocalDateTime.parse(times[i]) >= now.withMinute(0).withSecond(0).withNano(0) }
                .map { i ->
                    HourlyWeather(
                        time = LocalDateTime.parse(times[i]).atZone(ZoneId.of("Europe/Amsterdam")).toInstant(),
                        temperatureC = temps[i],
                        precipitationMm = precipitation[i],
                        precipitationProbabilityPct = precipitationProbability.get(i)?.takeIf { !it.isNull }?.asInt(),
                        weatherCode = weatherCodes[i],
                    )
                }
            return WeatherForecast(hours)
        }
    }
}
