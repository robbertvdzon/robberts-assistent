package nl.vdzon.robbertsassistent.gardenchat

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.util.UUID

/**
 * Firestore-opslag voor moestuin-chats: één document per conversatie in de collectie
 * `conversations`, met de berichten als array-veld. Firestore-calls zijn async (ApiFuture); we
 * blokkeren met `.get()` omdat de service synchroon is.
 */
class FirestoreConversationRepository(private val firestore: Firestore) : ConversationRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun create(): Conversation {
        val conversation = Conversation(id = UUID.randomUUID().toString(), createdAt = Instant.now())
        collection.document(conversation.id).set(conversation.toMap()).get()
        return conversation
    }

    override fun findById(id: String): Conversation? {
        val snapshot = collection.document(id).get().get()
        return if (snapshot.exists()) snapshot.toConversation() else null
    }

    override fun save(conversation: Conversation): Conversation {
        collection.document(conversation.id).set(conversation.toMap()).get()
        return conversation
    }

    private fun Conversation.toMap(): Map<String, Any> = mapOf(
        FIELD_CREATED_AT to createdAt.toEpochMilli(),
        FIELD_MESSAGES to messages.map { it.toMap() },
    )

    private fun GardenMessage.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "role" to role,
        "text" to text,
        "imageIds" to imageIds,
        "createdAt" to createdAt.toEpochMilli(),
    )

    private fun DocumentSnapshot.toConversation(): Conversation {
        val createdAt = getLong(FIELD_CREATED_AT) ?: 0L
        @Suppress("UNCHECKED_CAST")
        val rawMessages = get(FIELD_MESSAGES) as? List<Map<String, Any?>> ?: emptyList()
        val messages = rawMessages.map { m ->
            GardenMessage(
                id = m["id"] as? String ?: "",
                role = m["role"] as? String ?: GardenMessage.ROLE_USER,
                text = m["text"] as? String ?: "",
                imageIds = (m["imageIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = Instant.ofEpochMilli((m["createdAt"] as? Number)?.toLong() ?: 0L),
            )
        }
        return Conversation(id = id, messages = messages, createdAt = Instant.ofEpochMilli(createdAt))
    }

    private companion object {
        const val COLLECTION = "conversations"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_MESSAGES = "messages"
    }
}
