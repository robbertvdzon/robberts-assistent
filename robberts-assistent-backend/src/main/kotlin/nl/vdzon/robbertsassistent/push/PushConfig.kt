package nl.vdzon.robbertsassistent.push

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de FCM-token-opslag: Firestore zodra Firebase geconfigureerd is, anders in-memory
 * (fail-safe: bij een Firestore-fout terug naar in-memory).
 */
@Configuration
class PushConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun fcmTokenStore(firebase: FirebaseProvider): FcmTokenStore {
        if (!firebase.isConfigured) {
            logger.info("FCM-tokens: in-memory (geen Firebase-config)")
            return InMemoryFcmTokenStore()
        }
        return runCatching { FirestoreFcmTokenStore(firebase.firestore()) }
            .onSuccess { logger.info("FCM-tokens: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (fcm-tokens) faalde, val terug op in-memory", it)
                InMemoryFcmTokenStore()
            }
    }
}
