package nl.vdzon.robbertsassistent.assistant

import java.util.concurrent.atomic.AtomicReference

/**
 * Opslag-poort voor het gebruiker-brede geheugen: één vrije-tekst-string (niet meer een lijst van
 * losse items). Fallback is [InMemoryMemoryRepository]; met Firebase geconfigureerd kiest
 * [AssistantStoreConfig] de [FirestoreMemoryRepository]. Zelfde patroon als `notes.NotesRepository`.
 */
interface MemoryRepository {
    fun current(): String
    fun update(text: String): String
}

class InMemoryMemoryRepository : MemoryRepository {
    private val text = AtomicReference("")

    override fun current(): String = text.get()

    override fun update(text: String): String {
        this.text.set(text)
        return text
    }
}
