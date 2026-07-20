package nl.vdzon.robbertsassistent.assistant

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag-poort voor geheugen-items. Fallback is [InMemoryMemoryRepository]; met Firebase
 * geconfigureerd kiest [AssistantStoreConfig] de [FirestoreMemoryRepository].
 */
interface MemoryRepository {
    /** Meest recent bijgewerkt eerst. */
    fun listAll(): List<MemoryItem>

    fun findById(id: String): MemoryItem?

    fun create(text: String): MemoryItem

    /** `null` als het item niet bestaat. */
    fun update(id: String, text: String): MemoryItem?

    /** Verwijdert een item. Geen effect als het al niet (meer) bestaat. */
    fun delete(id: String)
}

class InMemoryMemoryRepository : MemoryRepository {
    private val store = ConcurrentHashMap<String, MemoryItem>()

    override fun listAll(): List<MemoryItem> = store.values.sortedByDescending { it.updatedAt }

    override fun findById(id: String): MemoryItem? = store[id]

    override fun create(text: String): MemoryItem {
        val now = Instant.now()
        val item = MemoryItem(id = UUID.randomUUID().toString(), text = text, createdAt = now, updatedAt = now)
        store[item.id] = item
        return item
    }

    override fun update(id: String, text: String): MemoryItem? {
        val existing = store[id] ?: return null
        val updated = existing.copy(text = text, updatedAt = Instant.now())
        store[id] = updated
        return updated
    }

    override fun delete(id: String) {
        store.remove(id)
    }
}
