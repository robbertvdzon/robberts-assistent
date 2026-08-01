package nl.vdzon.robbertsassistent.applaunch

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppLaunchStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun appLaunchRepository(firebase: FirebaseProvider): AppLaunchRepository {
        if (!firebase.isConfigured) {
            logger.info("App-launch-opslag: in-memory (geen Firebase-config)")
            return InMemoryAppLaunchRepository()
        }
        return runCatching { FirestoreAppLaunchRepository(firebase.firestore()) }
            .onSuccess { logger.info("App-launch-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory app-launches", it)
                InMemoryAppLaunchRepository()
            }
    }
}
