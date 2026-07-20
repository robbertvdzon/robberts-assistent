package nl.vdzon.robbertsassistent.strava

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.notifier.Notifier
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de Strava-client: de echte [StravaActivityClient] (OAuth refresh-token) zodra
 * RA_STRAVA_* geconfigureerd is, anders de stub. Zelfde stub-fallback-patroon als
 * [nl.vdzon.robbertsassistent.google.GoogleClientsConfig].
 */
@Configuration
class StravaClientConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun stravaClient(secrets: AppSecrets, notifier: Notifier): StravaClient {
        val clientId = secrets.stravaClientId
        val clientSecret = secrets.stravaClientSecret
        val refreshToken = secrets.stravaRefreshToken
        return if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
            logger.info("Strava: echte client actief")
            StravaActivityClient(StravaOAuthService(clientId, clientSecret, refreshToken, notifier))
        } else {
            logger.info("Strava: stub (geen RA_STRAVA_* config)")
            StubStravaClient()
        }
    }
}
