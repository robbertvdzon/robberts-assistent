package nl.vdzon.robbertsassistent.assistant

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.util.UUID

/**
 * Firestore-opslag voor geheugen-items: één document per item in de collectie
 * `assistant-memory`. Firestore-calls zijn async (ApiFuture); we blokkeren met `.get()` omdat de
 * service synchroon is (zelfde patroon als [FirestoreConversationRepository]).
 */
class FirestoreMemoryRepository(private val firestore: Firestore) : MemoryRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun listAll(): List<MemoryItem> =
        collection.get().get().documents.map { it.toMemoryItem() }.sortedByDescending { it.updatedAt }

    override fun findById(id: String): MemoryItem? {
        val snapshot = collection.document(id).get().get()
        return if (snapshot.exists()) snapshot.toMemoryItem() else null
    }

    override fun create(text: String): MemoryItem {
        val now = Instant.now()
        val item = MemoryItem(id = UUID.randomUUID().toString(), text = text, createdAt = now, updatedAt = now)
        collection.document(item.id).set(item.toMap()).get()
        return item
    }

    override fun update(id: String, text: String): MemoryItem? {
        val existing = findById(id) ?: return null
        val updated = existing.copy(text = text, updatedAt = Instant.now())
        collection.document(id).set(updated.toMap()).get()
        return updated
    }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun MemoryItem.toMap(): Map<String, Any> = mapOf(
        FIELD_TEXT to text,
        FIELD_CREATED_AT to createdAt.toEpochMilli(),
        FIELD_UPDATED_AT to updatedAt.toEpochMilli(),
    )

    private fun DocumentSnapshot.toMemoryItem(): MemoryItem {
        val createdAt = getLong(FIELD_CREATED_AT) ?: 0L
        val updatedAt = getLong(FIELD_UPDATED_AT) ?: createdAt
        return MemoryItem(
            id = id,
            text = getString(FIELD_TEXT) ?: "",
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private companion object {
        const val COLLECTION = "assistant-memory"
        const val FIELD_TEXT = "text"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
