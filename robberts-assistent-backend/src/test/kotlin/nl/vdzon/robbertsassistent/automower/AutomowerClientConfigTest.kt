package nl.vdzon.robbertsassistent.automower

import nl.vdzon.robbertsassistent.config.AppSecrets
import kotlin.test.Test
import kotlin.test.assertTrue

class AutomowerClientConfigTest {
    private val config = AutomowerClientConfig()

    private fun secrets(appKey: String? = null, appSecret: String? = null) =
        AppSecrets(
            rememberSecret = "x",
            googleClientId = "x",
            allowedEmails = setOf("robbert@vdzon.com"),
            husqvarnaAppKey = appKey,
            husqvarnaAppSecret = appSecret,
        )

    @Test
    fun `zonder app-key-secret valt de client terug op de stub`() {
        assertTrue(config.automowerClient(secrets()) is StubAutomowerClient)
    }

    @Test
    fun `met app-key en secret kiest de echte Husqvarna-client`() {
        assertTrue(config.automowerClient(secrets(appKey = "a", appSecret = "b")) is HusqvarnaAutomowerClient)
    }
}
