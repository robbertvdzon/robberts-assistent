package nl.vdzon.robbertsassistent.strava

import java.time.Duration
import java.time.Instant

/**
 * Vaste, deterministische trainingen — puur voor tests, zodat `StravaTools` zonder netwerk-call
 * getest kan worden (zelfde patroon als `StubCalendarClient`). [StravaClientConfig] kiest deze
 * zodra `RA_STRAVA_CLIENT_ID`/`_CLIENT_SECRET`/`_REFRESH_TOKEN` ontbreken.
 */
class StubStravaClient : StravaClient {
    override fun recentActivities(count: Int): StravaActivitiesResult {
        val now = Instant.now()
        val activities = listOf(
            StravaActivity(
                name = "Ochtendrit",
                sportType = "Ride",
                startDate = now.minus(Duration.ofDays(1)),
                distanceKm = 0.0,
                movingTimeMinutes = 47,
                averageHeartrate = 125.0,
                trainer = true,
            ),
            StravaActivity(
                name = "Rondje Heemskerk",
                sportType = "Run",
                startDate = now.minus(Duration.ofDays(3)),
                distanceKm = 8.2,
                movingTimeMinutes = 42,
                averageHeartrate = 148.0,
                trainer = false,
            ),
        )
        return StravaActivitiesResult(activities.take(count))
    }
}
