package nl.vdzon.robbertsassistent.assistant

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import java.time.Instant
import java.util.UUID

/**
 * Firestore-opslag voor assistent-gesprekken: één document per conversatie in de collectie
 * `assistant-conversations` (eigen collectie, los van de moestuin-chat), met de berichten als
 * array-veld. Firestore-calls zijn async (ApiFuture); we blokkeren met `.get()` omdat de service
 * synchroon is.
 */
class FirestoreConversationRepository(private val firestore: Firestore) : ConversationRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun create(): Conversation {
        val now = Instant.now()
        val conversation = Conversation(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
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

    override fun listAll(): List<Conversation> =
        collection.orderBy(FIELD_UPDATED_AT, Query.Direction.DESCENDING).get().get()
            .documents.map { it.toConversation() }

    private fun Conversation.toMap(): Map<String, Any> = mapOf(
        FIELD_TITLE to (title ?: ""),
        FIELD_CREATED_AT to createdAt.toEpochMilli(),
        FIELD_UPDATED_AT to updatedAt.toEpochMilli(),
        FIELD_MESSAGES to messages.map { it.toMap() },
    )

    private fun ConversationMessage.toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "role" to role,
        "text" to text,
        "imageIds" to imageIds,
        "createdAt" to createdAt.toEpochMilli(),
    )

    private fun DocumentSnapshot.toConversation(): Conversation {
        val title = getString(FIELD_TITLE)?.takeIf { it.isNotBlank() }
        val createdAt = getLong(FIELD_CREATED_AT) ?: 0L
        val updatedAt = getLong(FIELD_UPDATED_AT) ?: createdAt
        @Suppress("UNCHECKED_CAST")
        val rawMessages = get(FIELD_MESSAGES) as? List<Map<String, Any?>> ?: emptyList()
        val messages = rawMessages.map { m ->
            ConversationMessage(
                id = m["id"] as? String ?: "",
                role = m["role"] as? String ?: ConversationMessage.ROLE_USER,
                text = m["text"] as? String ?: "",
                imageIds = (m["imageIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = Instant.ofEpochMilli((m["createdAt"] as? Number)?.toLong() ?: 0L),
            )
        }
        return Conversation(
            id = id,
            title = title,
            messages = messages,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private companion object {
        const val COLLECTION = "assistant-conversations"
        const val FIELD_TITLE = "title"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_MESSAGES = "messages"
    }
}
