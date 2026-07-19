package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.weather.HourlyWeather
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WeatherForecast
import nl.vdzon.robbertsassistent.weather.weatherCodeDescription
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot de regen-/weersvoorspelling bij IJmuiden via [WeatherClient]
 * (Open-Meteo, keyless — geen secret nodig, zie CLAUDE.md §5).
 */
@Component
class WeatherTools(private val weatherClient: WeatherClient) {

    @Tool(
        description = "Voorspelling per uur voor de komende 6 uur bij IJmuiden (temperatuur, " +
            "neerslag in mm, kans op neerslag %). Gebruik dit voor vragen als 'gaat het (het " +
            "komende uur) regenen' of 'moet ik straks een paraplu mee'.",
    )
    fun getRainForecastNextHours(): String = format(weatherClient.hourlyForecast(NEAR_TERM_HOURS), allHours = true)

    @Tool(
        description = "Weersvoorspelling voor de komende 3 dagen bij IJmuiden, per 3 uur " +
            "(temperatuur, neerslag, weersomstandigheid zoals 'bewolkt' of 'regen'). Gebruik " +
            "getRainForecastNextHours voor vragen over de eerstkomende uren, dit voor verder vooruit.",
    )
    fun getWeatherForecast(): String = format(weatherClient.hourlyForecast(MULTI_DAY_HOURS), allHours = false)

    private fun format(forecast: WeatherForecast, allHours: Boolean): String {
        forecast.error?.let { return it }
        if (forecast.hours.isEmpty()) return "Open-Meteo gaf geen voorspellingsdata terug."
        val hours = if (allHours) forecast.hours else forecast.hours.filter { CHECKPOINT_HOURS.contains(hourOf(it)) }
        return hours.joinToString("\n") { line(it) }
    }

    private fun hourOf(hour: HourlyWeather): Int = hour.time.atZone(ZONE).hour

    private fun line(hour: HourlyWeather): String {
        val time = TIME_FORMATTER.format(hour.time.atZone(ZONE))
        val kans = hour.precipitationProbabilityPct?.let { ", $it% kans op neerslag" } ?: ""
        return "$time: ${hour.temperatureC}°C, ${weatherCodeDescription(hour.weatherCode)}, " +
            "${hour.precipitationMm} mm neerslag$kans"
    }

    private companion object {
        const val NEAR_TERM_HOURS = 6
        const val MULTI_DAY_HOURS = 72
        val CHECKPOINT_HOURS = setOf(8, 11, 14, 17, 20, 23)
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
