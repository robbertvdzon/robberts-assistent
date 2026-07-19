package nl.vdzon.robbertsassistent.alarms

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Kiest de alarm-opslag: Firestore als Firebase geconfigureerd is, anders in-memory (fail-safe). */
@Configuration
class AlarmRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun alarmRepository(firebase: FirebaseProvider): AlarmRepository {
        if (!firebase.isConfigured) {
            logger.info("Alarm-opslag: in-memory (geen Firebase-config)")
            return InMemoryAlarmRepository()
        }
        return runCatching { FirestoreAlarmRepository(firebase.firestore()) }
            .onSuccess { logger.info("Alarm-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (alarms) faalde, val terug op in-memory", it)
                InMemoryAlarmRepository()
            }
    }
}
