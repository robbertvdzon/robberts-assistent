package nl.vdzon.robbertsassistent.gardenchat

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor moestuin-chats. Fallback is [InMemoryConversationRepository]; met Firebase
 * geconfigureerd kiest [GardenStoreConfig] de [FirestoreConversationRepository].
 */
interface ConversationRepository {
    fun create(): Conversation
    fun findById(id: String): Conversation?
    fun save(conversation: Conversation): Conversation
}

class InMemoryConversationRepository : ConversationRepository {
    private val store = ConcurrentHashMap<String, Conversation>()

    override fun create(): Conversation {
        val conversation = Conversation(id = UUID.randomUUID().toString(), createdAt = Instant.now())
        store[conversation.id] = conversation
        return conversation
    }

    override fun findById(id: String): Conversation? = store[id]

    override fun save(conversation: Conversation): Conversation {
        store[conversation.id] = conversation
        return conversation
    }
}
