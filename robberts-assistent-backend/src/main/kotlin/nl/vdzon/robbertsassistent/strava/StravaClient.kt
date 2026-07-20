package nl.vdzon.robbertsassistent.strava

import java.time.Instant

/** Eén Strava-training. */
data class StravaActivity(
    val name: String,
    val sportType: String,
    val startDate: Instant,
    val distanceKm: Double,
    val movingTimeMinutes: Long,
    val averageHeartrate: Double?,
    val trainer: Boolean,
)

/**
 * Resultaat van een activiteiten-ophaal-poging. Bij een netwerk-/serverfout is [activities] leeg
 * en [error] gezet — de aanroeper (`StravaTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class StravaActivitiesResult(
    val activities: List<StravaActivity>,
    val error: String? = null,
)

/**
 * Robberts trainingen (Strava API v3, OAuth refresh-token — zelfde patroon als Google
 * Agenda/Docs). Actief zodra `RA_STRAVA_CLIENT_ID` + `_CLIENT_SECRET` + `_REFRESH_TOKEN` gezet
 * zijn (zie [nl.vdzon.robbertsassistent.config.AppSecrets]); anders [StubStravaClient].
 */
interface StravaClient {
    /** De meest recente trainingen, nieuwste eerst, tot maximaal [count]. */
    fun recentActivities(count: Int = 10): StravaActivitiesResult
}
