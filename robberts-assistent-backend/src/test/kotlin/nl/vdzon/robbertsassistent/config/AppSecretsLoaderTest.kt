package nl.vdzon.robbertsassistent.config

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSecretsLoaderTest {
    private fun baseEnv(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf(
            "RA_REMEMBER_SECRET" to "signing-secret",
            "RA_GOOGLE_CLIENT_ID" to "client-id.apps.googleusercontent.com",
        ) + extra

    @Test
    fun `loads app secrets from environment`() {
        val secrets = AppSecretsLoader(
            environment = baseEnv("RA_ALLOWED_EMAILS" to "robbert@vdzon.com"),
            secretFiles = emptyList(),
        ).load()

        assertEquals("signing-secret", secrets.rememberSecret)
        assertEquals("client-id.apps.googleusercontent.com", secrets.googleClientId)
        assertEquals(setOf("robbert@vdzon.com"), secrets.allowedEmails)
        assertFalse(secrets.previewSkipGoogleAuth)
    }

    @Test
    fun `defaults the allowlist to robbert when omitted`() {
        val secrets = AppSecretsLoader(
            environment = baseEnv(),
            secretFiles = emptyList(),
        ).load()

        assertEquals(setOf("robbert@vdzon.com"), secrets.allowedEmails)
    }

    @Test
    fun `parses a comma-separated allowlist and normalises whitespace and casing`() {
        val secrets = AppSecretsLoader(
            environment = baseEnv("RA_ALLOWED_EMAILS" to " Robbert@Vdzon.com , second@example.com "),
            secretFiles = emptyList(),
        ).load()

        assertEquals(setOf("robbert@vdzon.com", "second@example.com"), secrets.allowedEmails)
    }

    @Test
    fun `enables the preview Google-auth bypass`() {
        val secrets = AppSecretsLoader(
            environment = baseEnv("RA_PREVIEW_SKIP_GOOGLE_AUTH" to "true"),
            secretFiles = emptyList(),
        ).load()

        assertTrue(secrets.previewSkipGoogleAuth)
    }

    @Test
    fun `startup fails when google client id is omitted`() {
        val exception = assertFailsWith<IllegalStateException> {
            AppSecretsLoader(
                environment = mapOf("RA_REMEMBER_SECRET" to "signing-secret"),
                secretFiles = emptyList(),
            ).load()
        }
        assertContains(exception.message.orEmpty(), "RA_GOOGLE_CLIENT_ID")
    }

    @Test
    fun `startup fails when remember secret is omitted`() {
        val exception = assertFailsWith<IllegalStateException> {
            AppSecretsLoader(
                environment = mapOf("RA_GOOGLE_CLIENT_ID" to "client-id"),
                secretFiles = emptyList(),
            ).load()
        }
        assertContains(exception.message.orEmpty(), "RA_REMEMBER_SECRET")
    }
}
