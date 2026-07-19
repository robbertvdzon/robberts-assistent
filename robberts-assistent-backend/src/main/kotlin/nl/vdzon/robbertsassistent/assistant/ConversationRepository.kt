package nl.vdzon.robbertsassistent.assistant

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor assistent-gesprekken. Fallback is [InMemoryConversationRepository]; met
 * Firebase geconfigureerd kiest [AssistantStoreConfig] de [FirestoreConversationRepository].
 */
interface ConversationRepository {
    fun create(): Conversation
    fun findById(id: String): Conversation?
    fun save(conversation: Conversation): Conversation

    /** Alle gesprekken, meest recent bijgewerkt eerst. */
    fun listAll(): List<Conversation>
}

class InMemoryConversationRepository : ConversationRepository {
    private val store = ConcurrentHashMap<String, Conversation>()

    override fun create(): Conversation {
        val now = Instant.now()
        val conversation = Conversation(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
        store[conversation.id] = conversation
        return conversation
    }

    override fun findById(id: String): Conversation? = store[id]

    override fun save(conversation: Conversation): Conversation {
        store[conversation.id] = conversation
        return conversation
    }

    override fun listAll(): List<Conversation> = store.values.sortedByDescending { it.updatedAt }
}
