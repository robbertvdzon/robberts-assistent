package nl.vdzon.robbertsassistent.strava

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Leest Robberts trainingen via de Strava API v3 (`/athlete/activities`), met een access-token
 * uit [StravaOAuthService]. Bewust rauwe HTTP + Jackson (zoals `GoogleCalendarClient`) i.p.v. een
 * zware Strava-SDK.
 */
class StravaActivityClient(
    private val oauth: StravaOAuthService,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : StravaClient {

    private val objectMapper = jacksonObjectMapper()

    override fun recentActivities(count: Int): StravaActivitiesResult =
        runCatching {
            val request = HttpRequest.newBuilder(URI.create("$ACTIVITIES_URL?per_page=$count"))
                .header("Authorization", "Bearer ${oauth.accessToken()}")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                StravaActivitiesResult(emptyList(), "Kon Strava-trainingen niet ophalen (HTTP ${response.statusCode()}).")
            } else {
                StravaActivitiesResult(parseActivities(objectMapper.readTree(response.body())))
            }
        }.getOrElse { StravaActivitiesResult(emptyList(), "Kon Strava-trainingen niet ophalen: ${it.message}") }

    internal companion object {
        private const val ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities"

        /** Zet de ruwe Strava-JSON-array om naar een lijst [StravaActivity]. */
        internal fun parseActivities(root: JsonNode): List<StravaActivity> =
            root.mapNotNull { activity ->
                val startDate = activity.path("start_date").asText().takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                StravaActivity(
                    name = activity.path("name").asText("(zonder naam)"),
                    sportType = activity.path("sport_type").asText(activity.path("type").asText("onbekend")),
                    startDate = Instant.parse(startDate),
                    distanceKm = activity.path("distance").asDouble(0.0) / 1000.0,
                    movingTimeMinutes = activity.path("moving_time").asLong(0) / 60,
                    averageHeartrate = activity.path("average_heartrate").takeIf { !it.isMissingNode }?.asDouble(),
                    trainer = activity.path("trainer").asBoolean(false),
                )
            }
    }
}
