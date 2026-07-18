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
    fun conversationRepository(firebase: FirebaseProvider): ConversationRepository =
        if (firebase.isConfigured) {
            logger.info("Moestuin-chat opslag: Firestore")
            FirestoreConversationRepository(firebase.firestore())
        } else {
            logger.info("Moestuin-chat opslag: in-memory (geen Firebase-config)")
            InMemoryConversationRepository()
        }

    @Bean
    fun photoStorage(firebase: FirebaseProvider): PhotoStorage =
        if (firebase.isConfigured) {
            logger.info("Moestuin-foto's: Firebase Storage")
            FirebaseStoragePhotoStorage(firebase.bucket())
        } else {
            logger.info("Moestuin-foto's: in-memory (geen Firebase-config)")
            InMemoryPhotoStorage()
        }
}
