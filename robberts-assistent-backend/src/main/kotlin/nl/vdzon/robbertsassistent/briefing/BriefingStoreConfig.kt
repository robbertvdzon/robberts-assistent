package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kiest de opslag voor de briefing-caches en de weerkaart-PNG's: Firestore + Firebase Storage zodra
 * Firebase geconfigureerd is (zie [FirebaseProvider]), anders in-memory. Zelfde stub-fallback-
 * patroon als `assistant.AssistantStoreConfig`. Sinds SF-1275 zijn er twee onafhankelijke caches
 * (Upcoming + Health check, zie [BriefingService]), elk een los Firestore-document
 * (zie [FirestoreBriefingCacheRepository]) resp. een losse in-memory-instantie.
 */
@Configuration
class BriefingStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean("upcomingBriefingCache")
    fun upcomingBriefingCache(firebase: FirebaseProvider): BriefingCacheRepository =
        briefingCacheRepository(firebase, "Upcoming", FirestoreBriefingCacheRepository.DEFAULT_DOCUMENT)

    @Bean("healthBriefingCache")
    fun healthBriefingCache(firebase: FirebaseProvider): BriefingCacheRepository =
        briefingCacheRepository(firebase, "Health check", FirestoreBriefingCacheRepository.HEALTH_DOCUMENT)

    private fun briefingCacheRepository(firebase: FirebaseProvider, label: String, documentId: String): BriefingCacheRepository {
        if (!firebase.isConfigured) {
            logger.info("Briefing-cache opslag ($label): in-memory (geen Firebase-config)")
            return InMemoryBriefingCacheRepository()
        }
        return runCatching { FirestoreBriefingCacheRepository(firebase.firestore(), documentId) }
            .onSuccess { logger.info("Briefing-cache opslag ($label): Firestore") }
            .getOrElse {
                logger.error("Firestore-init (briefing-cache, $label) faalde, val terug op in-memory", it)
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
