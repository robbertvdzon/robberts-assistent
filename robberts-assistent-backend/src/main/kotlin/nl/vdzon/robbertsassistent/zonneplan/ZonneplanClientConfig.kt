package nl.vdzon.robbertsassistent.zonneplan

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de Zonneplan-client: de echte [HomeAssistantZonneplanClient] zodra
 * `RA_HOME_ASSISTENT_URL` + `_TOKEN` geconfigureerd zijn, anders de stub. Zelfde
 * stub-fallback-patroon als [nl.vdzon.robbertsassistent.automower.AutomowerClientConfig].
 */
@Configuration
class ZonneplanClientConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun zonneplanClient(secrets: AppSecrets): ZonneplanClient {
        val url = secrets.homeAssistantUrl
        val token = secrets.homeAssistantToken
        return if (!url.isNullOrBlank() && !token.isNullOrBlank()) {
            logger.info("Zonneplan: echte Home Assistant-client actief")
            HomeAssistantZonneplanClient(url, token)
        } else {
            logger.info("Zonneplan: stub (geen RA_HOME_ASSISTENT_URL/_TOKEN config)")
            StubZonneplanClient()
        }
    }
}
