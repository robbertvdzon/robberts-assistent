package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WatchStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun watchRepository(firebase: FirebaseProvider): WatchRepository {
        if (!firebase.isConfigured) {
            logger.info("Watch-opslag: in-memory (geen Firebase-config)")
            return InMemoryWatchRepository()
        }
        return runCatching { FirestoreWatchRepository(firebase.firestore()) }
            .onSuccess { logger.info("Watch-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory watches", it)
                InMemoryWatchRepository()
            }
    }
}
