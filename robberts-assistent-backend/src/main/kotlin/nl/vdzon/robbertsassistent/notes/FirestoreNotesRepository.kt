package nl.vdzon.robbertsassistent.notes

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.google.cloud.firestore.SetOptions
import java.time.Instant
import java.util.Date

/**
 * Bewaart de notitiedocumenten als `notes/<docId>` in Firestore, met de velden `title`, `order` en
 * `text`. De versiegeschiedenis staat per document in de subcollectie `notes/<docId>/versions`:
 * de velden `text` (String) en `savedAt` (tijdstip), met een door Firestore gegenereerd auto-id.
 *
 * Het bestaande document `notes/note` wordt bij de migratie hergebruikt als 'todo'-document, zodat
 * zowel de tekst als de al bestaande subcollectie `versions` blijven staan.
 */
class FirestoreNotesRepository(private val firestore: Firestore) : NotesRepository {

    private val collection get() = firestore.collection(COLLECTION)

    private fun reference(docId: String): DocumentReference = collection.document(docId)

    private fun versions(docId: String) = reference(docId).collection(SUBCOLLECTION_VERSIONS)

    // orderBy(title) laat documenten zónder `title`-veld automatisch weg — precies de eis dat een
    // document zonder notitievelden nooit als notitie in de lijst belandt. Sorteren op `order`
    // gebeurt daarna in geheugen (het gaat om een handvol documenten), dus geen extra index nodig.
    override fun documents(): List<NoteDocument> =
        collection.orderBy(FIELD_TITLE).get().get().documents
            .mapNotNull { it.toDocument() }
            .sortedWith(compareBy({ it.order }, { it.title }))

    override fun document(id: String): NoteDocument? = reference(id).get().get().toDocument()

    override fun createDocument(title: String, order: Int): NoteDocument {
        val reference = collection.document()
        reference.set(
            mapOf(FIELD_TITLE to title, FIELD_ORDER to order.toLong(), FIELD_TEXT to ""),
        ).get()
        return NoteDocument(id = reference.id, title = title, order = order, text = "")
    }

    override fun renameDocument(id: String, title: String) {
        reference(id).set(mapOf(FIELD_TITLE to title), SetOptions.merge()).get()
    }

    override fun deleteDocument(id: String) {
        versions(id).get().get().documents.forEach { it.reference.delete().get() }
        reference(id).delete().get()
    }

    override fun updateOrder(id: String, order: Int) {
        reference(id).set(mapOf(FIELD_ORDER to order.toLong()), SetOptions.merge()).get()
    }

    override fun updateText(id: String, text: String): String {
        reference(id).set(mapOf(FIELD_TEXT to text), SetOptions.merge()).get()
        return text
    }

    // `merge` zodat de bestaande tekst blijft staan als die er al is; de subcollectie `versions`
    // wordt sowieso niet geraakt door een schrijfactie op het document zelf.
    override fun createDefaultDocument(title: String): NoteDocument {
        val reference = reference(DEFAULT_DOCUMENT_ID)
        val text = reference.get().get().takeIf { it.exists() }?.getString(FIELD_TEXT).orEmpty()
        reference.set(
            mapOf(FIELD_TITLE to title, FIELD_ORDER to 0L, FIELD_TEXT to text),
            SetOptions.merge(),
        ).get()
        return NoteDocument(id = DEFAULT_DOCUMENT_ID, title = title, order = 0, text = text)
    }

    override fun addVersion(documentId: String, text: String, savedAt: Instant): NoteVersion {
        val reference = versions(documentId).document()
        reference.set(
            mapOf(
                FIELD_TEXT to text,
                FIELD_SAVED_AT to Timestamp.of(Date.from(savedAt)),
            ),
        ).get()
        return NoteVersion(id = reference.id, text = text, savedAt = savedAt)
    }

    override fun latestVersions(documentId: String, limit: Int): List<NoteVersion> =
        versions(documentId).orderBy(FIELD_SAVED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .get().get().documents
            .mapNotNull { it.toVersion() }

    override fun version(documentId: String, id: String): NoteVersion? =
        versions(documentId).document(id).get().get().toVersion()

    override fun allVersions(documentId: String): List<NoteVersion> =
        versions(documentId).orderBy(FIELD_SAVED_AT, Query.Direction.DESCENDING)
            .get().get().documents
            .mapNotNull { it.toVersion() }

    override fun deleteVersion(documentId: String, id: String) {
        versions(documentId).document(id).delete().get()
    }

    private fun DocumentSnapshot.toDocument(): NoteDocument? {
        val title = getString(FIELD_TITLE) ?: return null
        return NoteDocument(
            id = id,
            title = title,
            order = getLong(FIELD_ORDER)?.toInt() ?: 0,
            text = getString(FIELD_TEXT).orEmpty(),
        )
    }

    private fun DocumentSnapshot.toVersion(): NoteVersion? {
        val text = getString(FIELD_TEXT) ?: return null
        val savedAt = getTimestamp(FIELD_SAVED_AT)?.toDate()?.toInstant() ?: return null
        return NoteVersion(id = id, text = text, savedAt = savedAt)
    }

    private companion object {
        const val COLLECTION = "notes"
        const val SUBCOLLECTION_VERSIONS = "versions"
        const val FIELD_TITLE = "title"
        const val FIELD_ORDER = "order"
        const val FIELD_TEXT = "text"
        const val FIELD_SAVED_AT = "savedAt"
    }
}
