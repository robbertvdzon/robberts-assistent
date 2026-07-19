package nl.vdzon.robbertsassistent.airquality

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
 * Echte luchtkwaliteit-/UV-/pollenvoorspelling via de Open-Meteo Air-Quality-API
 * (air-quality-api.open-meteo.com) — gratis, geen API-key. Coördinaten: Luttik Cie 12, Heemskerk
 * (Robberts moestuin, zelfde als [nl.vdzon.robbertsassistent.weather.OpenMeteoWeatherClient]).
 */
@Component
class OpenMeteoAirQualityClient(private val httpClient: HttpClient = HttpClient.newHttpClient()) : AirQualityClient {

    private val objectMapper = jacksonObjectMapper()

    override fun hourlyForecast(hours: Int): AirQualityForecast =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create(FORECAST_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                AirQualityForecast(emptyList(), "Kon Open-Meteo Air Quality niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                parseForecast(objectMapper.readTree(response.body())).let { it.copy(hours = it.hours.take(hours)) }
            }
        }.getOrElse { AirQualityForecast(emptyList(), "Kon Open-Meteo Air Quality niet ophalen: ${it.message}") }

    internal companion object {
        // Luttik Cie 12, Heemskerk (moestuin). Gratis, geen API-key nodig.
        private const val FORECAST_URL = "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude=52.5078&longitude=4.6420" +
            "&hourly=european_aqi,pm10,pm2_5,uv_index,birch_pollen,grass_pollen,ragweed_pollen" +
            "&forecast_days=3&timezone=Europe%2FAmsterdam"

        /** Zet de ruwe Open-Meteo-JSON om naar een oplopende lijst [HourlyAirQuality], vanaf het huidige uur. */
        internal fun parseForecast(root: JsonNode): AirQualityForecast {
            val hourly = root.path("hourly")
            val times = hourly.path("time").map { it.asText() }
            if (times.isEmpty()) return AirQualityForecast(emptyList(), "Open-Meteo gaf geen luchtkwaliteitsdata terug.")

            val aqi = hourly.path("european_aqi")
            val pm10 = hourly.path("pm10")
            val pm25 = hourly.path("pm2_5")
            val uv = hourly.path("uv_index")
            val birch = hourly.path("birch_pollen")
            val grass = hourly.path("grass_pollen")
            val ragweed = hourly.path("ragweed_pollen")

            val now = LocalDateTime.now(ZoneId.of("Europe/Amsterdam")).withMinute(0).withSecond(0).withNano(0)
            val hours = times.indices
                .filter { i -> LocalDateTime.parse(times[i]) >= now }
                .map { i ->
                    HourlyAirQuality(
                        time = LocalDateTime.parse(times[i]).atZone(ZoneId.of("Europe/Amsterdam")).toInstant(),
                        europeanAqi = aqi.get(i)?.takeIf { !it.isNull }?.asInt(),
                        pm10 = pm10.get(i)?.takeIf { !it.isNull }?.asDouble(),
                        pm25 = pm25.get(i)?.takeIf { !it.isNull }?.asDouble(),
                        uvIndex = uv.get(i)?.takeIf { !it.isNull }?.asDouble(),
                        birchPollen = birch.get(i)?.takeIf { !it.isNull }?.asDouble(),
                        grassPollen = grass.get(i)?.takeIf { !it.isNull }?.asDouble(),
                        ragweedPollen = ragweed.get(i)?.takeIf { !it.isNull }?.asDouble(),
                    )
                }
            return AirQualityForecast(hours)
        }
    }
}
