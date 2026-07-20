package nl.vdzon.robbertsassistent.strava

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor Strava (trainingen, OAuth refresh-token). */
@Component
class StravaCouplingProbe(private val secrets: AppSecrets, private val stravaClient: StravaClient) : CouplingProbe {

    private val stravaConfigured: Boolean
        get() = !secrets.stravaClientId.isNullOrBlank() &&
            !secrets.stravaClientSecret.isNullOrBlank() &&
            !secrets.stravaRefreshToken.isNullOrBlank()

    override val id = "strava"
    override val name = "Strava"
    override val description = "Robberts trainingen (OAuth refresh-token)."
    override val configured: Boolean get() = stravaConfigured
    override val mode: String get() = if (stravaConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!stravaConfigured) return false to "niet geconfigureerd (stub)"
        val result = stravaClient.recentActivities(1)
        return result.error?.let { false to it } ?: (true to "${result.activities.size} training(en) gevonden")
    }
}
