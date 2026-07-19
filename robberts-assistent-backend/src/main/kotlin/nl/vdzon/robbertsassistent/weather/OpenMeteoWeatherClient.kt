package nl.vdzon.robbertsassistent.weather

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Echte weersvoorspelling via de Open-Meteo forecast-API (open-meteo.com) — gratis, geen API-key.
 * Coördinaten: IJmuiden Zuidpier, zelfde locatie als de wind-tool.
 */
@Component
class OpenMeteoWeatherClient(private val httpClient: HttpClient = HttpClient.newHttpClient()) : WeatherClient {

    private val objectMapper = jacksonObjectMapper()

    override fun hourlyForecast(hours: Int): WeatherForecast =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(FORECAST_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                WeatherForecast(emptyList(), "Kon Open-Meteo niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                parseForecast(objectMapper.readTree(response.body())).let { it.copy(hours = it.hours.take(hours)) }
            }
        }.getOrElse { WeatherForecast(emptyList(), "Kon Open-Meteo niet ophalen: ${it.message}") }

    internal companion object {
        // IJmuiden Zuidpier-coördinaten (zelfde als WindTools). Gratis, geen API-key nodig.
        private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=52.4614&longitude=4.5552" +
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
