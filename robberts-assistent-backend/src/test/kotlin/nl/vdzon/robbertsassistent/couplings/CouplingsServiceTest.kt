package nl.vdzon.robbertsassistent.couplings

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.google.StubCalendarClient
import nl.vdzon.robbertsassistent.push.InMemoryFcmTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CouplingsServiceTest {

    private fun service(secrets: AppSecrets) =
        CouplingsService(secrets, FirebaseProvider(secrets), StubCalendarClient(), InMemoryFcmTokenStore())

    private val bareSecrets = AppSecrets(
        rememberSecret = "s",
        googleClientId = "c",
        allowedEmails = setOf("robbert@vdzon.com"),
    )

    @Test
    fun `zonder secrets staat alles op fallback en niets geconfigureerd`() {
        val statuses = service(bareSecrets).statuses()

        assertEquals(setOf("openai", "telegram", "firestore", "storage", "google", "fcm"), statuses.map { it.id }.toSet())
        assertEquals(true, statuses.all { !it.configured }, "geen enkele koppeling zou geconfigureerd moeten zijn")
        assertEquals(true, statuses.all { it.mode == "fallback" }, "alles zou op fallback moeten staan")
        assertEquals(true, statuses.all { it.test == null }, "de lijst-weergave doet geen live-test")
    }

    @Test
    fun `met secrets gaan koppelingen op echt`() {
        val configured = bareSecrets.copy(
            openAiApiKey = "sk-test",
            telegramBotToken = "bot",
            telegramChatId = "123",
            firebaseCredentialsJson = "{}",
            firebaseProjectId = "tuinbewatering",
            firebaseStorageBucket = "tuinbewatering.firebasestorage.app",
            googleOAuthClientId = "gid",
            googleOAuthClientSecret = "gsecret",
            googleOAuthRefreshToken = "grefresh",
        )

        val byId = service(configured).statuses().associateBy { it.id }

        assertEquals("echt", byId.getValue("openai").mode)
        assertEquals("echt", byId.getValue("telegram").mode)
        assertEquals("echt", byId.getValue("firestore").mode)
        assertEquals("echt", byId.getValue("storage").mode)
        assertEquals("echt", byId.getValue("google").mode)
        assertEquals("echt", byId.getValue("fcm").mode)
        assertEquals(true, byId.values.all { it.configured })
    }
}
