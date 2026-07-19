package nl.vdzon.robbertsassistent.google

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor Google Agenda + Docs (read-only, OAuth refresh-token). */
@Component
class GoogleCouplingProbe(private val secrets: AppSecrets, private val calendarClient: CalendarClient) : CouplingProbe {

    private val googleOAuthConfigured: Boolean
        get() = !secrets.googleOAuthClientId.isNullOrBlank() &&
            !secrets.googleOAuthClientSecret.isNullOrBlank() &&
            !secrets.googleOAuthRefreshToken.isNullOrBlank()

    override val id = "google"
    override val name = "Google Agenda + Docs"
    override val description = "Read-only agenda en documenten (OAuth refresh-token)."
    override val configured: Boolean get() = googleOAuthConfigured
    override val mode: String get() = if (googleOAuthConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!googleOAuthConfigured) return false to "niet geconfigureerd (stub)"
        val events = calendarClient.upcoming(1)
        return true to "agenda gelezen (${events.size} afspraak vooruit)"
    }
}
