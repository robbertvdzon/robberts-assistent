package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.airquality.AirQualityClient
import nl.vdzon.robbertsassistent.airquality.AirQualityForecast
import nl.vdzon.robbertsassistent.airquality.HourlyAirQuality
import nl.vdzon.robbertsassistent.airquality.europeanAqiDescription
import nl.vdzon.robbertsassistent.airquality.uvIndexDescription
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot luchtkwaliteit, UV-index en pollen bij de moestuin
 * (Luttik Cie 12, Heemskerk) via [AirQualityClient] (Open-Meteo, keyless — geen secret nodig,
 * zie CLAUDE.md §5).
 */
@Component
class AirQualityTools(private val airQualityClient: AirQualityClient) {

    @Tool(
        description = "Luchtkwaliteit (Europese luchtkwaliteitsindex, fijnstof PM10/PM2.5) en " +
            "UV-index bij de moestuin in Heemskerk, per 3 uur voor de komende 2 dagen. Gebruik " +
            "dit voor vragen als 'hoe is de luchtkwaliteit' of 'hoe hoog is de UV-index vandaag'.",
    )
    fun getAirQualityAndUvForecast(): String = format(airQualityClient.hourlyForecast(MULTI_DAY_HOURS)) { hour ->
        val aqi = hour.europeanAqi?.let { "AQI $it (${europeanAqiDescription(it)})" } ?: "AQI onbekend"
        val uv = hour.uvIndex?.let { "UV-index ${it} (${uvIndexDescription(it)})" } ?: "UV-index onbekend"
        val fijnstof = "PM10 ${hour.pm10 ?: "?"} µg/m³, PM2.5 ${hour.pm25 ?: "?"} µg/m³"
        "$aqi, $fijnstof, $uv"
    }

    @Tool(
        description = "Pollenconcentraties (berk, gras, bijvoet/ambrosia) bij de moestuin in " +
            "Heemskerk, per 3 uur voor de komende 2 dagen. Gebruik dit voor vragen over hooikoorts " +
            "of pollenallergie.",
    )
    fun getPollenForecast(): String = format(airQualityClient.hourlyForecast(MULTI_DAY_HOURS)) { hour ->
        "berkenpollen ${hour.birchPollen ?: "?"}, graspollen ${hour.grassPollen ?: "?"}, " +
            "bijvoet-/ambrosiapollen ${hour.ragweedPollen ?: "?"} (korrels/m³)"
    }

    private fun format(forecast: AirQualityForecast, line: (HourlyAirQuality) -> String): String {
        forecast.error?.let { return it }
        if (forecast.hours.isEmpty()) return "Open-Meteo gaf geen luchtkwaliteitsdata terug."
        val checkpoints = forecast.hours.filter { CHECKPOINT_HOURS.contains(it.time.atZone(ZONE).hour) }
        return checkpoints.joinToString("\n") { "${TIME_FORMATTER.format(it.time.atZone(ZONE))}: ${line(it)}" }
    }

    private companion object {
        const val MULTI_DAY_HOURS = 48
        val CHECKPOINT_HOURS = setOf(8, 11, 14, 17, 20, 23)
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
