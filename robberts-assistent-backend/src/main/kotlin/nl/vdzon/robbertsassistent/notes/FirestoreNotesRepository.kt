package nl.vdzon.robbertsassistent.notes

import com.google.cloud.firestore.Firestore

/** Bewaart de ene notitie als één document `notes/note` (veld `text`) in Firestore. */
class FirestoreNotesRepository(private val firestore: Firestore) : NotesRepository {

    private val document get() = firestore.collection(COLLECTION).document(DOCUMENT)

    override fun current(): String {
        val snapshot = document.get().get()
        return if (snapshot.exists()) snapshot.getString(FIELD_TEXT).orEmpty() else ""
    }

    override fun update(text: String): String {
        document.set(mapOf(FIELD_TEXT to text)).get()
        return text
    }

    private companion object {
        const val COLLECTION = "notes"
        const val DOCUMENT = "note"
        const val FIELD_TEXT = "text"
    }
}
