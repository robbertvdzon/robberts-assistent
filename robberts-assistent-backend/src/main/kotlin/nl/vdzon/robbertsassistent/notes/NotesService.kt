package nl.vdzon.robbertsassistent.notes

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Bewaart de ene notitie-string via [NotesRepository] (Firestore in prod, in-memory als fallback)
 * en houdt daarnaast een versiegeschiedenis bij: elke [update] die de tekst daadwerkelijk
 * verandert levert een [NoteVersion] op.
 */
@Service
class NotesService(
    private val repository: NotesRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun current(): String = repository.current()

    /**
     * Slaat [text] op als de huidige notitie en bewaart daarna een versie-record — behalve als de
     * tekst identiek is aan de meest recente bestaande versie (anders zou de 10-seconden-autosave
     * van de app dubbels opleveren). Het wegschrijven van de versie is best-effort: faalt dat, dan
     * blijft de notitie zelf gewoon opgeslagen.
     */
    fun update(text: String): String {
        val saved = repository.update(text)
        runCatching {
            val latest = repository.latestVersions(1).firstOrNull()
            if (latest == null || latest.text != text) {
                repository.addVersion(text, now())
            }
        }.onFailure { logger.warn("Bewaren van notitie-versie mislukt: {}", it.message) }
        return saved
    }

    /** De nieuwste versies (nieuwste eerst), begrensd op [MAX_VERSIONS]. */
    fun versions(limit: Int = MAX_VERSIONS): List<NoteVersion> =
        repository.latestVersions(limit.coerceIn(1, MAX_VERSIONS))

    fun version(id: String): NoteVersion? = repository.version(id)

    companion object {
        /** Maximaal aantal versies dat het overzichts-endpoint teruggeeft. */
        const val MAX_VERSIONS = 200
    }
}
