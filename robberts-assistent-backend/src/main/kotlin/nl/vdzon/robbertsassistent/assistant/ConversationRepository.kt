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

    /**
     * Gesprekken, meest recent bijgewerkt eerst. Zonder [includeArchived] worden gearchiveerde
     * gesprekken weggelaten. [limit]/[offset] pagineren over het (gefilterde) resultaat; zonder
     * [limit] komt alles terug vanaf [offset].
     */
    fun listAll(includeArchived: Boolean = false, limit: Int? = null, offset: Int = 0): List<Conversation>

    /** Verwijdert een gesprek. Geen effect als het al niet (meer) bestaat. */
    fun delete(id: String)
}

/** Filtert/sorteert/pagineert een ruwe lijst gesprekken — gedeeld door alle [ConversationRepository]-implementaties. */
fun List<Conversation>.paginated(includeArchived: Boolean, limit: Int?, offset: Int): List<Conversation> {
    val filtered = if (includeArchived) this else filter { !it.archived }
    val sorted = filtered.sortedByDescending { it.updatedAt }
    val dropped = sorted.drop(offset.coerceAtLeast(0))
    return if (limit != null) dropped.take(limit) else dropped
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

    override fun listAll(includeArchived: Boolean, limit: Int?, offset: Int): List<Conversation> =
        store.values.toList().paginated(includeArchived, limit, offset)

    override fun delete(id: String) {
        store.remove(id)
    }
}
