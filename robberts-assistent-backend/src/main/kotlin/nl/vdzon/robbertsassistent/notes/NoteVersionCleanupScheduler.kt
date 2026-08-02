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
    private val repository: NotesRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Amsterdam")
    fun cleanup() {
        runCatching {
            val ids = NoteVersionCleanup.idsToDelete(repository.allVersions(), now())
            ids.forEach { repository.deleteVersion(it) }
            logger.info("Notitie-versies opgeruimd: {} verwijderd", ids.size)
        }.onFailure { logger.warn("Opruimen van notitie-versies mislukt: {}", it.message) }
    }
}
