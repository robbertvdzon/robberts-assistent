package nl.vdzon.robbertsassistent.reminders

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

/**
 * Kiest de reminder-opslag: [FirestoreReminderRepository] zodra Firebase geconfigureerd is
 * (RA_FIREBASE_CREDENTIALS_FILE + RA_FIREBASE_PROJECT_ID), anders [InMemoryReminderRepository].
 * Zelfde stub-fallback-patroon als de Notifier: zonder secret draait alles gewoon in-memory.
 */
@Configuration
class ReminderRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun reminderRepository(secrets: AppSecrets): ReminderRepository {
        val credentialsFile = secrets.firebaseCredentialsFile
        val projectId = secrets.firebaseProjectId
        return if (!credentialsFile.isNullOrBlank() && !projectId.isNullOrBlank()) {
            logger.info("Reminder-opslag: Firestore (project {})", projectId)
            FirestoreReminderRepository(firestore(credentialsFile, projectId))
        } else {
            logger.info("Reminder-opslag: in-memory (geen RA_FIREBASE_* config)")
            InMemoryReminderRepository()
        }
    }

    private fun firestore(credentialsFile: String, projectId: String): Firestore {
        val credentials = FileInputStream(credentialsFile).use { GoogleCredentials.fromStream(it) }
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(projectId)
            .build()
        val app = if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        } else {
            FirebaseApp.getInstance()
        }
        return FirestoreClient.getFirestore(app)
    }
}
