package nl.vdzon.robbertsassistent.notes

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Ruimt 's nachts oude notitie-versies op (zie [NoteVersionCleanup] voor de regel). Zelfde stijl
 * als `briefing.BriefingCacheScheduler`: de hele run zit in een `runCatching`, zodat een fout in
 * Firestore de applicatie niet raakt.
 *
 * Per run gaat er precies één INFO-regel uit met het aantal verwijderde versies, terug te vinden
 * via `oc logs deploy/robberts-assistent-backend -n robberts-assistent`.
 */
@Component
class NoteVersionCleanupScheduler(
    private val notesService: NotesService,
    private val repository: NotesRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Loopt over álle notitiedocumenten en past de regel per document toe. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam")
    fun cleanup() {
        runCatching {
            val moment = now()
            var deleted = 0
            notesService.documents().forEach { document ->
                val ids = NoteVersionCleanup.idsToDelete(repository.allVersions(document.id), moment)
                ids.forEach { repository.deleteVersion(document.id, it) }
                deleted += ids.size
            }
            logger.info("Notitie-versies opgeruimd: {} verwijderd", deleted)
        }.onFailure { logger.warn("Opruimen van notitie-versies mislukt: {}", it.message) }
    }
}
