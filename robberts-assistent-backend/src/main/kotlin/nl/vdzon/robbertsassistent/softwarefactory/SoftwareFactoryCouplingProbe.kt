package nl.vdzon.robbertsassistent.softwarefactory

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.stereotype.Component

/** Koppelingsstatus voor de software-factory-bridge (stories, actiepunten). */
@Component
class SoftwareFactoryCouplingProbe(
    private val secrets: AppSecrets,
    private val client: SoftwareFactoryClient,
) : CouplingProbe {

    private val softwareFactoryConfigured: Boolean
        get() = !secrets.softwareFactoryGoogleClientSecret.isNullOrBlank() &&
            !secrets.softwareFactoryGoogleRefreshToken.isNullOrBlank()

    override val id = "softwarefactory"
    override val name = "Software Factory"
    override val description = "Stories en actiepunten uit de software-factory-dashboard."
    override val configured: Boolean get() = softwareFactoryConfigured
    override val mode: String get() = if (softwareFactoryConfigured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        if (!softwareFactoryConfigured) return false to "niet geconfigureerd (stub)"
        val result = client.stories()
        return result.error?.let { false to it } ?: (true to "${result.stories.size} stories opgehaald")
    }
}
