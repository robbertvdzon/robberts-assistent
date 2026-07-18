package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class ReminderRepositoryConfigTest {

    private fun provider(credentialsFile: String? = null, projectId: String? = null) =
        FirebaseProvider(
            AppSecrets(
                rememberSecret = "x",
                googleClientId = "x",
                allowedEmails = setOf("robbert@vdzon.com"),
                firebaseCredentialsFile = credentialsFile,
                firebaseProjectId = projectId,
            ),
        )

    @Test
    fun `zonder firebase-config valt terug op in-memory`() {
        val repo = ReminderRepositoryConfig().reminderRepository(provider())
        assertTrue(repo is InMemoryReminderRepository)
    }

    @Test
    fun `alleen een project-id zonder credentials valt terug op in-memory`() {
        val repo = ReminderRepositoryConfig().reminderRepository(provider(projectId = "demo-project"))
        assertTrue(repo is InMemoryReminderRepository)
    }
}
