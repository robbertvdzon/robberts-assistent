package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de watch-opslag: [FirestoreWatchRepository] zodra Firebase geconfigureerd is (zie
 * [FirebaseProvider]), anders [InMemoryWatchRepository]. Zelfde stub-fallback-patroon als
 * `reminders.ReminderRepositoryConfig`.
 */
@Configuration
class WatchRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun watchRepository(firebase: FirebaseProvider): WatchRepository {
        if (!firebase.isConfigured) {
            logger.info("Watch-opslag: in-memory (geen Firebase-config)")
            return InMemoryWatchRepository()
        }
        // Fail-safe: een Firebase-init-fout mag de app niet laten crashen (anders blijft de oude
        // pod staan). Val dan terug op in-memory en log de oorzaak.
        return runCatching { FirestoreWatchRepository(firebase.firestore()) }
            .onSuccess { logger.info("Watch-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory watches", it)
                InMemoryWatchRepository()
            }
    }
}
