package nl.vdzon.robbertsassistent.google

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.notifier.Notifier
import kotlin.test.Test
import kotlin.test.assertTrue

class GoogleClientsConfigTest {
    private val config = GoogleClientsConfig()
    private val notifier = object : Notifier {
        override fun send(message: String) {}
    }

    private fun secrets(clientId: String? = null, clientSecret: String? = null, refreshToken: String? = null) =
        AppSecrets(
            rememberSecret = "x",
            googleClientId = "x",
            allowedEmails = setOf("robbert@vdzon.com"),
            googleOAuthClientId = clientId,
            googleOAuthClientSecret = clientSecret,
            googleOAuthRefreshToken = refreshToken,
        )

    @Test
    fun `zonder oauth-config vallen agenda en docs terug op stubs`() {
        assertTrue(config.calendarClient(secrets(), notifier) is StubCalendarClient)
        assertTrue(config.docsClient(secrets(), notifier) is StubDocsClient)
    }

    @Test
    fun `met volledige oauth-config kiest de echte Google-clients`() {
        val s = secrets(clientId = "a", clientSecret = "b", refreshToken = "c")
        assertTrue(config.calendarClient(s, notifier) is GoogleCalendarClient)
        assertTrue(config.docsClient(s, notifier) is GoogleDocsClient)
    }
}
