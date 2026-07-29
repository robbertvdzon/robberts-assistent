package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import kotlin.test.Test
import kotlin.test.assertTrue

class WatchRepositoryConfigTest {

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
        val repo = WatchRepositoryConfig().watchRepository(provider())
        assertTrue(repo is InMemoryWatchRepository)
    }

    @Test
    fun `alleen een project-id zonder credentials valt terug op in-memory`() {
        val repo = WatchRepositoryConfig().watchRepository(provider(projectId = "demo-project"))
        assertTrue(repo is InMemoryWatchRepository)
    }
}
