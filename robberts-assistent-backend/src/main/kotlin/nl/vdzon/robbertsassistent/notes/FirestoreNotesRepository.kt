package nl.vdzon.robbertsassistent.notes

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import java.time.Instant
import java.util.Date

/**
 * Bewaart de ene notitie als één document `notes/note` (veld `text`) in Firestore.
 *
 * De versiegeschiedenis staat in de subcollectie `notes/note/versions`: per document de velden
 * `text` (String) en `savedAt` (tijdstip), met een door Firestore gegenereerd auto-id. Het
 * document `notes/note` zelf blijft ongewijzigd de huidige tekst.
 */
class FirestoreNotesRepository(private val firestore: Firestore) : NotesRepository {

    private val document get() = firestore.collection(COLLECTION).document(DOCUMENT)
    private val versions get() = document.collection(SUBCOLLECTION_VERSIONS)

    override fun current(): String {
        val snapshot = document.get().get()
        return if (snapshot.exists()) snapshot.getString(FIELD_TEXT).orEmpty() else ""
    }

    override fun update(text: String): String {
        document.set(mapOf(FIELD_TEXT to text)).get()
        return text
    }

    override fun addVersion(text: String, savedAt: Instant): NoteVersion {
        val reference = versions.document()
        reference.set(
            mapOf(
                FIELD_TEXT to text,
                FIELD_SAVED_AT to Timestamp.of(Date.from(savedAt)),
            ),
        ).get()
        return NoteVersion(id = reference.id, text = text, savedAt = savedAt)
    }

    override fun latestVersions(limit: Int): List<NoteVersion> =
        versions.orderBy(FIELD_SAVED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .get().get().documents
            .mapNotNull { it.toVersion() }

    override fun version(id: String): NoteVersion? = versions.document(id).get().get().toVersion()

    override fun allVersions(): List<NoteVersion> =
        versions.orderBy(FIELD_SAVED_AT, Query.Direction.DESCENDING)
            .get().get().documents
            .mapNotNull { it.toVersion() }

    override fun deleteVersion(id: String) {
        versions.document(id).delete().get()
    }

    private fun DocumentSnapshot.toVersion(): NoteVersion? {
        val text = getString(FIELD_TEXT) ?: return null
        val savedAt = getTimestamp(FIELD_SAVED_AT)?.toDate()?.toInstant() ?: return null
        return NoteVersion(id = id, text = text, savedAt = savedAt)
    }

    private companion object {
        const val COLLECTION = "notes"
        const val DOCUMENT = "note"
        const val SUBCOLLECTION_VERSIONS = "versions"
        const val FIELD_TEXT = "text"
        const val FIELD_SAVED_AT = "savedAt"
    }
}
