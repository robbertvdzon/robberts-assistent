package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.strava.StravaActivity
import nl.vdzon.robbertsassistent.strava.StravaClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Geeft de chat-assistent toegang tot Robberts trainingen via [StravaClient] (OAuth refresh-token,
 * zie CLAUDE.md §5).
 */
@Component
class StravaTools(private val stravaClient: StravaClient) {

    @Tool(
        description = "Haal Robberts meest recente trainingen op (fietsen, hardlopen, enz.): naam, " +
            "type, datum, duur, afstand en gemiddelde hartslag. Gebruik dit voor vragen als 'wat " +
            "waren mijn laatste trainingen' of 'hoeveel heb ik deze week getraind'.",
    )
    fun getRecentActivities(): String {
        val result = stravaClient.recentActivities(DEFAULT_COUNT)
        result.error?.let { return it }
        if (result.activities.isEmpty()) return "Geen trainingen gevonden."
        return result.activities.joinToString("\n") { line(it) }
    }

    private fun line(activity: StravaActivity): String {
        val date = TIME_FORMATTER.format(activity.startDate.atZone(ZONE))
        val trainer = if (activity.trainer) ", indoor" else ""
        val distance = if (activity.distanceKm > 0) ", ${"%.1f".format(activity.distanceKm)} km" else ""
        val heartrate = activity.averageHeartrate?.let { ", gem. hartslag ${it.toInt()}" } ?: ""
        return "$date: ${activity.name} (${activity.sportType}$trainer), " +
            "${activity.movingTimeMinutes} min$distance$heartrate"
    }

    private companion object {
        const val DEFAULT_COUNT = 10
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
