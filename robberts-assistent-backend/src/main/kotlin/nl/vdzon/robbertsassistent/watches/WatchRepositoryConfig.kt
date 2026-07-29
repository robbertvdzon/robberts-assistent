package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de opslag van zoekopdrachten: [FirestoreWatchRepository] zodra Firebase geconfigureerd is
 * (zie [FirebaseProvider]), anders [InMemoryWatchRepository]. Exact hetzelfde stub-fallback-patroon
 * als `reminders.ReminderRepositoryConfig`: zonder secret draait alles in-memory, en een
 * init-fout mag de app nooit laten crashen.
 */
@Configuration
class WatchRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun watchRepository(firebase: FirebaseProvider): WatchRepository {
        if (!firebase.isConfigured) {
            logger.info("Zoekopdracht-opslag: in-memory (geen Firebase-config)")
            return InMemoryWatchRepository()
        }
        return runCatching { FirestoreWatchRepository(firebase.firestore()) }
            .onSuccess { logger.info("Zoekopdracht-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory zoekopdrachten", it)
                InMemoryWatchRepository()
            }
    }
}
