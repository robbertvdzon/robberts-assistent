package nl.vdzon.robbertsassistent.strava

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.notifier.Notifier
import kotlin.test.Test
import kotlin.test.assertTrue

class StravaClientConfigTest {
    private val config = StravaClientConfig()
    private val notifier = object : Notifier {
        override fun send(message: String) {}
    }

    private fun secrets(clientId: String? = null, clientSecret: String? = null, refreshToken: String? = null) =
        AppSecrets(
            rememberSecret = "x",
            googleClientId = "x",
            allowedEmails = setOf("robbert@vdzon.com"),
            stravaClientId = clientId,
            stravaClientSecret = clientSecret,
            stravaRefreshToken = refreshToken,
        )

    @Test
    fun `zonder oauth-config valt de client terug op de stub`() {
        assertTrue(config.stravaClient(secrets(), notifier) is StubStravaClient)
    }

    @Test
    fun `met volledige oauth-config kiest de echte Strava-client`() {
        val s = secrets(clientId = "a", clientSecret = "b", refreshToken = "c")
        assertTrue(config.stravaClient(s, notifier) is StravaActivityClient)
    }
}
