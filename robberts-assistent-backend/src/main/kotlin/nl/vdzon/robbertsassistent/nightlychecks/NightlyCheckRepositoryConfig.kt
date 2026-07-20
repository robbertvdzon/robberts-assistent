package nl.vdzon.robbertsassistent.nightlychecks

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de nightly-check-opslag: [FirestoreNightlyCheckRepository] zodra Firebase geconfigureerd
 * is, anders [InMemoryNightlyCheckRepository]. Zelfde stub-fallback-patroon als
 * `ReminderRepositoryConfig`.
 */
@Configuration
class NightlyCheckRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun nightlyCheckRepository(firebase: FirebaseProvider): NightlyCheckRepository {
        if (!firebase.isConfigured) {
            logger.info("Nightly-check-opslag: in-memory (geen Firebase-config)")
            return InMemoryNightlyCheckRepository()
        }
        return runCatching { FirestoreNightlyCheckRepository(firebase.firestore()) }
            .onSuccess { logger.info("Nightly-check-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory nightly-checks", it)
                InMemoryNightlyCheckRepository()
            }
    }
}
