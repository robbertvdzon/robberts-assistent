package nl.vdzon.robbertsassistent.softwarefactory

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de Software Factory-client: de echte [BridgeSoftwareFactoryClient] zodra
 * `RA_SOFTWAREFACTORY_CLIENT_SECRET` + `_REFRESH_TOKEN` geconfigureerd zijn, anders de stub.
 * Zelfde stub-fallback-patroon als [nl.vdzon.robbertsassistent.google.GoogleClientsConfig].
 */
@Configuration
class SoftwareFactoryClientConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun softwareFactoryClient(secrets: AppSecrets): SoftwareFactoryClient {
        val clientSecret = secrets.softwareFactoryGoogleClientSecret
        val refreshToken = secrets.softwareFactoryGoogleRefreshToken
        return if (!clientSecret.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
            logger.info("Software Factory: echte bridge-client actief")
            BridgeSoftwareFactoryClient(secrets.googleClientId, clientSecret, refreshToken)
        } else {
            logger.info("Software Factory: stub (geen RA_SOFTWAREFACTORY_CLIENT_SECRET/_REFRESH_TOKEN config)")
            StubSoftwareFactoryClient()
        }
    }
}
