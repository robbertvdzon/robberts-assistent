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
 * Echte windvoorspelling via de Open-Meteo forecast-API (gratis, geen API-key), zelfde bron als
 * `assistant.ai.WindTools` maar dan gestructureerd (kn + graden i.p.v. platte tekst). Coördinaten:
 * Wijk aan Zee-strand, relevant voor de aanlandige-wind-check in de kite-briefingsectie.
 */
@Component
class OpenMeteoWindForecastClient(private val httpClient: HttpClient = HttpClient.newHttpClient()) : WindForecastClient {

    private val objectMapper = jacksonObjectMapper()

    override fun hourlyForecast(hours: Int): WindForecast =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(FORECAST_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                WindForecast(emptyList(), "Kon Open-Meteo-wind niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                parseForecast(objectMapper.readTree(response.body())).let { it.copy(hours = it.hours.take(hours)) }
            }
        }.getOrElse { WindForecast(emptyList(), "Kon Open-Meteo-wind niet ophalen: ${it.message}") }

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
