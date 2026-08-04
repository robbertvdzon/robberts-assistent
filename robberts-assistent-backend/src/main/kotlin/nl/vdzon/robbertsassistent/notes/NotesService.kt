package nl.vdzon.robbertsassistent.notes

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Beheert Robberts notitiedocumenten via [NotesRepository] (Firestore in prod, in-memory als
 * fallback) en houdt per document een versiegeschiedenis bij: elke [updateText] die de tekst
 * daadwerkelijk verandert levert een [NoteVersion] van dát document op.
 *
 * De migratie van de oude "ene notitie" naar het 'todo'-document gebeurt lazy en idempotent bij
 * elke documenten-toegang, zie [ensureDocuments].
 */
@Service
class NotesService(
    private val repository: NotesRepository,
    private val now: () -> Instant = Instant::now,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Zorgt dat er minstens één document is en geeft de actuele lijst (op volgorde) terug.
     *
     * Bestaan er al documenten (met titel), dan gebeurt er niets. Anders wordt het bestaande
     * `notes/note` het 'todo'-document, met behoud van tekst én de subcollectie `versions`.
     * Twee keer draaien levert dus nooit twee 'todo'-documenten op.
     */
    @Synchronized
    private fun ensureDocuments(): List<NoteDocument> {
        val existing = repository.documents()
        if (existing.isNotEmpty()) return existing
        val migrated = repository.createDefaultDocument(DEFAULT_DOCUMENT_TITLE)
        logger.info("Notitiedocumenten gemigreerd: standaarddocument '{}' aangemaakt", migrated.title)
        return listOf(migrated)
    }

    /** Alle documenten op volgorde; triggert de migratie. */
    fun documents(): List<NoteDocument> = ensureDocuments()

    /** Eén document op id; onbekend id => [NoteDocumentNotFoundException]. */
    fun document(id: String): NoteDocument {
        ensureDocuments()
        return repository.document(id) ?: throw NoteDocumentNotFoundException(notFoundMessage(id))
    }

    /**
     * Het document waarop de oude endpoints, `briefing.WeekTasksSectionProvider` en de naamloze
     * AI-tools werken: het document met id [DEFAULT_DOCUMENT_ID]. Is dat (later) verwijderd, dan
     * het eerste document in de volgorde — er is altijd minstens één document.
     */
    fun defaultDocument(): NoteDocument {
        val documents = ensureDocuments()
        return documents.firstOrNull { it.id == DEFAULT_DOCUMENT_ID } ?: documents.first()
    }

    /** Nieuw, leeg document onderaan de volgorde. */
    fun createDocument(title: String): NoteDocument {
        val existing = ensureDocuments()
        val clean = validateTitle(title, existing, ownId = null)
        return repository.createDocument(clean, (existing.maxOfOrNull { it.order } ?: -1) + 1)
    }

    fun renameDocument(id: String, title: String): NoteDocument {
        val existing = ensureDocuments()
        val document = existing.firstOrNull { it.id == id }
            ?: throw NoteDocumentNotFoundException(notFoundMessage(id))
        val clean = validateTitle(title, existing, ownId = id)
        repository.renameDocument(id, clean)
        return document.copy(title = clean)
    }

    /** Verwijdert het document inclusief zijn versies; het laatste document blijft staan (409). */
    fun deleteDocument(id: String) {
        val existing = ensureDocuments()
        existing.firstOrNull { it.id == id }
            ?: throw NoteDocumentNotFoundException(notFoundMessage(id))
        if (existing.size <= 1) {
            throw NoteDocumentConflictException("Het laatste notitiedocument kan niet verwijderd worden")
        }
        repository.deleteDocument(id)
    }

    /**
     * Herschrijft de volgorde naar dichte posities 0..n-1: eerst de meegegeven [ids] in die
     * volgorde, daarna de niet-genoemde documenten met hun onderlinge volgorde intact.
     */
    fun reorder(ids: List<String>) {
        val existing = ensureDocuments()
        val byId = existing.associateBy { it.id }
        ids.forEach { byId[it] ?: throw NoteDocumentNotFoundException(notFoundMessage(it)) }
        val named = ids.distinct().mapNotNull { byId[it] }
        val namedIds = named.map(NoteDocument::id).toSet()
        val rest = existing.filterNot { it.id in namedIds }
        (named + rest).forEachIndexed { index, document ->
            if (document.order != index) repository.updateOrder(document.id, index)
        }
    }

    /**
     * Slaat [text] op als inhoud van [documentId] en bewaart daarna een versie-record — behalve als
     * de tekst identiek is aan de meest recente bestaande versie van dát document (anders zou de
     * 10-seconden-autosave van de app dubbels opleveren). Het wegschrijven van de versie is
     * best-effort: faalt dat, dan blijft de notitie zelf gewoon opgeslagen.
     */
    fun updateText(documentId: String, text: String): String {
        document(documentId)
        val saved = repository.updateText(documentId, text)
        runCatching {
            val latest = repository.latestVersions(documentId, 1).firstOrNull()
            if (latest == null || latest.text != text) {
                repository.addVersion(documentId, text, now())
            }
        }.onFailure { logger.warn("Bewaren van notitie-versie mislukt: {}", it.message) }
        return saved
    }

    /** De nieuwste versies van [documentId] (nieuwste eerst), begrensd op [MAX_VERSIONS]. */
    fun versions(documentId: String, limit: Int = MAX_VERSIONS): List<NoteVersion> {
        document(documentId)
        return repository.latestVersions(documentId, limit.coerceIn(1, MAX_VERSIONS))
    }

    /** Eén versie binnen [documentId]; onbekend document-id => [NoteDocumentNotFoundException]. */
    fun version(documentId: String, versionId: String): NoteVersion? {
        document(documentId)
        return repository.version(documentId, versionId)
    }

    // --- Backwards compatibility: de oude endpoints/tools werken op het standaarddocument. ---

    fun current(): String = defaultDocument().text

    fun update(text: String): String = updateText(defaultDocument().id, text)

    fun versions(limit: Int = MAX_VERSIONS): List<NoteVersion> = versions(defaultDocument().id, limit)

    fun version(id: String): NoteVersion? = version(defaultDocument().id, id)

    private fun validateTitle(title: String, existing: List<NoteDocument>, ownId: String?): String {
        val clean = title.trim()
        if (clean.isEmpty()) throw NoteTitleInvalidException("Geef een titel op")
        if (clean.length > MAX_TITLE_LENGTH) {
            throw NoteTitleInvalidException("Een titel mag maximaal $MAX_TITLE_LENGTH tekens lang zijn")
        }
        val duplicate = existing.any { it.id != ownId && it.title.trim().equals(clean, ignoreCase = true) }
        if (duplicate) throw NoteDocumentConflictException("Er bestaat al een notitiedocument '$clean'")
        return clean
    }

    private fun notFoundMessage(id: String) = "Notitiedocument '$id' niet gevonden"

    companion object {
        /** Maximaal aantal versies dat het overzichts-endpoint teruggeeft. */
        const val MAX_VERSIONS = 200
    }
}
