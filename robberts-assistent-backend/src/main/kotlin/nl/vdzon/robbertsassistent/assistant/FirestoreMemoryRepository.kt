package nl.vdzon.robbertsassistent.assistant

import com.google.cloud.firestore.Firestore

/** Bewaart het geheugen als één document `assistant-memory/memory` (veld `text`) in Firestore. */
class FirestoreMemoryRepository(private val firestore: Firestore) : MemoryRepository {

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
        const val COLLECTION = "assistant-memory"
        const val DOCUMENT = "memory"
        const val FIELD_TEXT = "text"
    }
}
