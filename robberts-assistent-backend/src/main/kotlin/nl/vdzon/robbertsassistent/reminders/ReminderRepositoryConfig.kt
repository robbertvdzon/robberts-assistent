package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de reminder-opslag: [FirestoreReminderRepository] zodra Firebase geconfigureerd is (zie
 * [FirebaseProvider]), anders [InMemoryReminderRepository]. Zelfde stub-fallback-patroon als de
 * Notifier: zonder secret draait alles in-memory.
 */
@Configuration
class ReminderRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun reminderRepository(firebase: FirebaseProvider): ReminderRepository {
        if (!firebase.isConfigured) {
            logger.info("Reminder-opslag: in-memory (geen Firebase-config)")
            return InMemoryReminderRepository()
        }
        // Fail-safe: een Firebase-init-fout mag de app niet laten crashen (anders blijft de oude
        // pod staan). Val dan terug op in-memory en log de oorzaak.
        return runCatching { FirestoreReminderRepository(firebase.firestore()) }
            .onSuccess { logger.info("Reminder-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory reminders", it)
                InMemoryReminderRepository()
            }
    }
}
