package nl.vdzon.robbertsassistent.gardenchat

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de opslag voor de moestuin-chat: Firestore + Firebase Storage zodra Firebase geconfigureerd
 * is (zie [FirebaseProvider]), anders in-memory. Zelfde stub-fallback-patroon als de rest.
 */
@Configuration
class GardenStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun conversationRepository(firebase: FirebaseProvider): ConversationRepository {
        if (!firebase.isConfigured) {
            logger.info("Moestuin-chat opslag: in-memory (geen Firebase-config)")
            return InMemoryConversationRepository()
        }
        return runCatching { FirestoreConversationRepository(firebase.firestore()) }
            .onSuccess { logger.info("Moestuin-chat opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (chat) faalde, val terug op in-memory", it)
                InMemoryConversationRepository()
            }
    }

    @Bean
    fun photoStorage(firebase: FirebaseProvider): PhotoStorage {
        if (!firebase.isConfigured) {
            logger.info("Moestuin-foto's: in-memory (geen Firebase-config)")
            return InMemoryPhotoStorage()
        }
        return runCatching { FirebaseStoragePhotoStorage(firebase.bucket()) }
            .onSuccess { logger.info("Moestuin-foto's: Firebase Storage") }
            .getOrElse {
                logger.error("Firebase Storage-init faalde, val terug op in-memory foto's", it)
                InMemoryPhotoStorage()
            }
    }
}
