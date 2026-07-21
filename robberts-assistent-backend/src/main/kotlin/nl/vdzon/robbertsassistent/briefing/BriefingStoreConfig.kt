package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de opslag voor de briefing-cache en de weerkaart-PNG's: Firestore + Firebase Storage zodra
 * Firebase geconfigureerd is (zie [FirebaseProvider]), anders in-memory. Zelfde stub-fallback-
 * patroon als `assistant.AssistantStoreConfig`.
 */
@Configuration
class BriefingStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun briefingCacheRepository(firebase: FirebaseProvider): BriefingCacheRepository {
        if (!firebase.isConfigured) {
            logger.info("Briefing-cache opslag: in-memory (geen Firebase-config)")
            return InMemoryBriefingCacheRepository()
        }
        return runCatching { FirestoreBriefingCacheRepository(firebase.firestore()) }
            .onSuccess { logger.info("Briefing-cache opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (briefing-cache) faalde, val terug op in-memory", it)
                InMemoryBriefingCacheRepository()
            }
    }

    @Bean
    fun weatherMapStorage(firebase: FirebaseProvider): WeatherMapStorage {
        if (!firebase.isConfigured) {
            logger.info("Weerkaart-opslag: in-memory (geen Firebase-config)")
            return InMemoryWeatherMapStorage()
        }
        return runCatching { FirebaseStorageWeatherMapStorage(firebase.bucket()) }
            .onSuccess { logger.info("Weerkaart-opslag: Firebase Storage") }
            .getOrElse {
                logger.error("Firebase Storage-init (weerkaart) faalde, val terug op in-memory", it)
                InMemoryWeatherMapStorage()
            }
    }
}
