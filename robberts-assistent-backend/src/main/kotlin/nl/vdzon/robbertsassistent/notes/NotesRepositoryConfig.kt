package nl.vdzon.robbertsassistent.notes

import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Kiest de notitie-opslag: Firestore als Firebase geconfigureerd is, anders in-memory (fail-safe). */
@Configuration
class NotesRepositoryConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun notesRepository(firebase: FirebaseProvider): NotesRepository {
        if (!firebase.isConfigured) {
            logger.info("Notitie-opslag: in-memory (geen Firebase-config)")
            return InMemoryNotesRepository()
        }
        return runCatching { FirestoreNotesRepository(firebase.firestore()) }
            .onSuccess { logger.info("Notitie-opslag: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (notes) faalde, val terug op in-memory", it)
                InMemoryNotesRepository()
            }
    }
}
