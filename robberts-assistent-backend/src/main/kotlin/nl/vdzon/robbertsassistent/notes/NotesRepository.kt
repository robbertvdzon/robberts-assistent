package nl.vdzon.robbertsassistent.notes

import java.time.Instant
import java.util.UUID

/**
 * Opslag-poort voor Robberts notitiedocumenten plus de versiegeschiedenis per document.
 * Firestore in prod, in-memory fallback zonder Firebase.
 * (Voorheen één notitie-string; sinds SF-1892 meerdere documenten.)
 */
interface NotesRepository {

    /** Alle notitiedocumenten op [NoteDocument.order]; documenten zonder titel tellen niet mee. */
    fun documents(): List<NoteDocument>

    /** Eén document op id, of `null` als het niet (meer) bestaat. */
    fun document(id: String): NoteDocument?

    /** Maakt een leeg document met [title] op positie [order] en geeft het aan. */
    fun createDocument(title: String, order: Int): NoteDocument

    fun renameDocument(id: String, title: String)

    /** Verwijdert het document inclusief al zijn versies. */
    fun deleteDocument(id: String)

    fun updateOrder(id: String, order: Int)

    /** Schrijft de tekst van [id] weg en geeft de opgeslagen tekst terug. */
    fun updateText(id: String, text: String): String

    /**
     * Maakt het standaarddocument ([DEFAULT_DOCUMENT_ID]) aan met [title], mét behoud van de
     * tekst die er al stond en zónder de subcollectie `versions` aan te raken. Alleen aan te
     * roepen als er nog geen documenten zijn (de lazy migratie).
     */
    fun createDefaultDocument(title: String): NoteDocument

    /** Bewaart [text] als nieuwe versie van [documentId] en geeft het opgeslagen record terug. */
    fun addVersion(documentId: String, text: String, savedAt: Instant): NoteVersion

    /** De [limit] nieuwste versies van [documentId], nieuwste eerst. */
    fun latestVersions(documentId: String, limit: Int): List<NoteVersion>

    /** Eén versie op id binnen [documentId], of `null` als die niet (meer) bestaat. */
    fun version(documentId: String, id: String): NoteVersion?

    /** Alle versies van [documentId], nieuwste eerst — bedoeld voor het nachtelijke opruimen. */
    fun allVersions(documentId: String): List<NoteVersion>

    /** Verwijdert de versie met [id] uit [documentId]; een onbekend id is geen fout. */
    fun deleteVersion(documentId: String, id: String)
}

/**
 * In-memory fallback. [legacyText] staat voor de tekst die vóór de migratie in `notes/note` stond;
 * die komt bij [createDefaultDocument] in het 'todo'-document terecht, net als in Firestore.
 */
class InMemoryNotesRepository(private val legacyText: String = "") : NotesRepository {
    private val lock = Any()
    private val documents = linkedMapOf<String, NoteDocument>()
    private val versions = linkedMapOf<String, MutableList<NoteVersion>>()

    override fun documents(): List<NoteDocument> =
        synchronized(lock) { documents.values.sortedWith(compareBy({ it.order }, { it.title })) }

    override fun document(id: String): NoteDocument? = synchronized(lock) { documents[id] }

    override fun createDocument(title: String, order: Int): NoteDocument = synchronized(lock) {
        val document = NoteDocument(id = UUID.randomUUID().toString(), title = title, order = order)
        documents[document.id] = document
        document
    }

    override fun renameDocument(id: String, title: String) {
        synchronized(lock) { documents[id]?.let { documents[id] = it.copy(title = title) } }
    }

    override fun deleteDocument(id: String) {
        synchronized(lock) {
            documents.remove(id)
            versions.remove(id)
        }
    }

    override fun updateOrder(id: String, order: Int) {
        synchronized(lock) { documents[id]?.let { documents[id] = it.copy(order = order) } }
    }

    override fun updateText(id: String, text: String): String = synchronized(lock) {
        documents[id]?.let { documents[id] = it.copy(text = text) }
        text
    }

    override fun createDefaultDocument(title: String): NoteDocument = synchronized(lock) {
        val document = documents[DEFAULT_DOCUMENT_ID]
            ?.copy(title = title, order = 0)
            ?: NoteDocument(id = DEFAULT_DOCUMENT_ID, title = title, order = 0, text = legacyText)
        documents[DEFAULT_DOCUMENT_ID] = document
        document
    }

    override fun addVersion(documentId: String, text: String, savedAt: Instant): NoteVersion {
        val version = NoteVersion(id = UUID.randomUUID().toString(), text = text, savedAt = savedAt)
        synchronized(lock) { versions.getOrPut(documentId) { mutableListOf() }.add(version) }
        return version
    }

    override fun latestVersions(documentId: String, limit: Int): List<NoteVersion> =
        allVersions(documentId).take(limit)

    override fun version(documentId: String, id: String): NoteVersion? =
        synchronized(lock) { versions[documentId]?.firstOrNull { it.id == id } }

    // `asReversed()` vóór het (stabiele) sorteren zodat bij een gelijk tijdstip de laatst
    // toegevoegde versie vooraan staat — anders zou een save binnen dezelfde milliseconde
    // als "oudste" gelden.
    override fun allVersions(documentId: String): List<NoteVersion> = synchronized(lock) {
        versions[documentId].orEmpty().asReversed().sortedByDescending { it.savedAt }
    }

    override fun deleteVersion(documentId: String, id: String) {
        synchronized(lock) { versions[documentId]?.removeAll { it.id == id } }
    }
}
