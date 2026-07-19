package nl.vdzon.robbertsassistent.assistant

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Een opgeslagen foto: de ruwe bytes + het content-type (bv. image/jpeg). */
class StoredPhoto(val bytes: ByteArray, val contentType: String)

/**
 * Opslag-poort voor foto's in assistent-gesprekken. Fallback is [InMemoryPhotoStorage]
 * (ephemeral); met Firebase geconfigureerd kiest [AssistantStoreConfig] de
 * [FirebaseStoragePhotoStorage].
 */
interface PhotoStorage {
    /** Bewaart een foto en geeft een id terug waarmee 'ie later op te halen is. */
    fun store(bytes: ByteArray, contentType: String): String

    fun load(id: String): StoredPhoto?
}

class InMemoryPhotoStorage : PhotoStorage {
    private val store = ConcurrentHashMap<String, StoredPhoto>()

    override fun store(bytes: ByteArray, contentType: String): String {
        val id = UUID.randomUUID().toString()
        store[id] = StoredPhoto(bytes, contentType)
        return id
    }

    override fun load(id: String): StoredPhoto? = store[id]
}
