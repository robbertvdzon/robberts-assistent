package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.tides.TideClient
import nl.vdzon.robbertsassistent.tides.TideExtreme
import nl.vdzon.robbertsassistent.tides.TideType
import nl.vdzon.robbertsassistent.tides.WaterLevel
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot de getijvoorspelling bij IJmuiden (buitenhaven) via
 * [TideClient] (Rijkswaterstaat WaterWebservices, keyless — geen secret nodig, zie CLAUDE.md §5).
 * Relevant voor stranduitjes/kite.
 */
@Component
class TideTools(private val tideClient: TideClient) {

    @Tool(
        description = "Waterhoogte (t.o.v. NAP) per uur voor de komende 24 uur bij IJmuiden, " +
            "buitenhaven. Gebruik dit voor vragen als 'hoe hoog staat het water vanmiddag' of " +
            "'hoe hoog staat het water om 15:00'.",
    )
    fun getWaterLevelForecast(): String {
        val forecast = tideClient.forecast(NEAR_TERM_HOURS)
        forecast.error?.let { return it }
        if (forecast.levels.isEmpty()) return "RWS gaf geen getijvoorspelling terug."
        val hourly = forecast.levels.filter { it.time.atZone(ZONE).minute == 0 }
        return hourly.joinToString("\n") { levelLine(it) }
    }

    @Tool(
        description = "Aankomende hoog- en laagwatermomenten (tijdstip + waterhoogte t.o.v. NAP) " +
            "bij IJmuiden, buitenhaven, voor de komende 2 dagen. Gebruik dit voor vragen als " +
            "'wanneer is het hoogwater/laagwater' of 'kan ik zo aan het strand fietsen/kiten'.",
    )
    fun getTideExtremes(): String {
        val forecast = tideClient.forecast(MULTI_DAY_HOURS)
        forecast.error?.let { return it }
        if (forecast.extremes.isEmpty()) return "RWS gaf geen hoog-/laagwatermomenten terug."
        return forecast.extremes.joinToString("\n") { extremeLine(it) }
    }

    private fun levelLine(level: WaterLevel): String =
        "${TIME_FORMATTER.format(level.time.atZone(ZONE))}: ${level.heightCm} cm t.o.v. NAP"

    private fun extremeLine(extreme: TideExtreme): String {
        val type = if (extreme.type == TideType.HOOGWATER) "hoogwater" else "laagwater"
        return "${TIME_FORMATTER.format(extreme.time.atZone(ZONE))}: $type, ${extreme.heightCm} cm t.o.v. NAP"
    }

    private companion object {
        const val NEAR_TERM_HOURS = 24
        const val MULTI_DAY_HOURS = 48
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
