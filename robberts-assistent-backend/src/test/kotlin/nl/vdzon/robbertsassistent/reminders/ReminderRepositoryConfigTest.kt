package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.config.AppSecrets
import kotlin.test.Test
import kotlin.test.assertTrue

class ReminderRepositoryConfigTest {

    private fun secrets(credentialsFile: String? = null, projectId: String? = null) = AppSecrets(
        rememberSecret = "x",
        googleClientId = "x",
        allowedEmails = setOf("robbert@vdzon.com"),
        firebaseCredentialsFile = credentialsFile,
        firebaseProjectId = projectId,
    )

    @Test
    fun `zonder firebase-config valt terug op in-memory`() {
        val repo = ReminderRepositoryConfig().reminderRepository(secrets())
        assertTrue(repo is InMemoryReminderRepository)
    }

    @Test
    fun `alleen een project-id zonder credentials-bestand valt terug op in-memory`() {
        val repo = ReminderRepositoryConfig().reminderRepository(secrets(projectId = "demo-project"))
        assertTrue(repo is InMemoryReminderRepository)
    }
}
