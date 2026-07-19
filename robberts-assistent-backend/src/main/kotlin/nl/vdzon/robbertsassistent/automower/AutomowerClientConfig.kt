package nl.vdzon.robbertsassistent.automower

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de Automower-client: de echte [HusqvarnaAutomowerClient] zodra `RA_HUSQVARNA_APP_KEY` +
 * `_APP_SECRET` geconfigureerd zijn, anders de stub. Zelfde stub-fallback-patroon als
 * [nl.vdzon.robbertsassistent.google.GoogleClientsConfig].
 */
@Configuration
class AutomowerClientConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun automowerClient(secrets: AppSecrets): AutomowerClient {
        val appKey = secrets.husqvarnaAppKey
        val appSecret = secrets.husqvarnaAppSecret
        return if (!appKey.isNullOrBlank() && !appSecret.isNullOrBlank()) {
            logger.info("Automower: echte Husqvarna-client actief")
            HusqvarnaAutomowerClient(appKey, appSecret)
        } else {
            logger.info("Automower: stub (geen RA_HUSQVARNA_APP_KEY/_SECRET config)")
            StubAutomowerClient()
        }
    }
}
