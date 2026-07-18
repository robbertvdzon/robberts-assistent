package nl.vdzon.robbertsassistent.google

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.notifier.Notifier
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de Google-clients: de echte [GoogleCalendarClient]/[GoogleDocsClient] (OAuth refresh-token)
 * zodra RA_GOOGLE_OAUTH_* geconfigureerd is, anders de stubs. Zelfde stub-fallback-patroon als de
 * Notifier en reminder-opslag: zonder secrets draait alles op stub-data.
 */
@Configuration
class GoogleClientsConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun calendarClient(secrets: AppSecrets, notifier: Notifier): CalendarClient =
        oauthOrNull(secrets, notifier)?.let {
            logger.info("Agenda: echte Google Calendar-client actief")
            GoogleCalendarClient(it)
        } ?: run {
            logger.info("Agenda: stub (geen RA_GOOGLE_OAUTH_* config)")
            StubCalendarClient()
        }

    @Bean
    fun docsClient(secrets: AppSecrets, notifier: Notifier): DocsClient =
        oauthOrNull(secrets, notifier)?.let {
            logger.info("Docs: echte Google Docs-client actief")
            GoogleDocsClient(it)
        } ?: run {
            logger.info("Docs: stub (geen RA_GOOGLE_OAUTH_* config)")
            StubDocsClient()
        }

    private fun oauthOrNull(secrets: AppSecrets, notifier: Notifier): GoogleOAuthService? {
        val clientId = secrets.googleOAuthClientId
        val clientSecret = secrets.googleOAuthClientSecret
        val refreshToken = secrets.googleOAuthRefreshToken
        return if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
            GoogleOAuthService(clientId, clientSecret, refreshToken, notifier)
        } else {
            null
        }
    }
}
