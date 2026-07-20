package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de opslag voor de assistent-gesprekken: Firestore + Firebase Storage zodra Firebase
 * geconfigureerd is (zie [FirebaseProvider]), anders in-memory. Zelfde stub-fallback-patroon als
 * `gardenchat.GardenStoreConfig`. Expliciete bean-namen (`assistantConversationRepository`/
 * `assistantPhotoStorage`) omdat `gardenchat.GardenStoreConfig` beans met dezelfde methodenaam
 * (maar een ander, module-eigen poort-type) registreert.
 */
@Configuration
class AssistantStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean("assistantConversationRepository")
    fun conversationRepository(firebase: FirebaseProvider): ConversationRepository {
        if (!firebase.isConfigured) {
            logger.info("Assistent-gesprekken opslag: in-memory (geen Firebase-config)")
            return InMemoryConversationRepository()
        }
        return runCatching { FirestoreConversationRepository(firebase.firestore()) }
            .onSuccess { logger.info("Assistent-gesprekken opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (assistent-gesprekken) faalde, val terug op in-memory", it)
                InMemoryConversationRepository()
            }
    }

    @Bean("assistantPhotoStorage")
    fun photoStorage(firebase: FirebaseProvider): PhotoStorage {
        if (!firebase.isConfigured) {
            logger.info("Assistent-gespreksfoto's: in-memory (geen Firebase-config)")
            return InMemoryPhotoStorage()
        }
        return runCatching { FirebaseStoragePhotoStorage(firebase.bucket()) }
            .onSuccess { logger.info("Assistent-gespreksfoto's: Firebase Storage") }
            .getOrElse {
                logger.error("Firebase Storage-init faalde, val terug op in-memory foto's", it)
                InMemoryPhotoStorage()
            }
    }

    @Bean("assistantMemoryRepository")
    fun memoryRepository(firebase: FirebaseProvider): MemoryRepository {
        if (!firebase.isConfigured) {
            logger.info("Assistent-geheugen opslag: in-memory (geen Firebase-config)")
            return InMemoryMemoryRepository()
        }
        return runCatching { FirestoreMemoryRepository(firebase.firestore()) }
            .onSuccess { logger.info("Assistent-geheugen opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (assistent-geheugen) faalde, val terug op in-memory", it)
                InMemoryMemoryRepository()
            }
    }
}
