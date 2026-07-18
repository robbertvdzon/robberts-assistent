package nl.vdzon.robbertsassistent.gardenchat

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Een opgeslagen foto: de ruwe bytes + het content-type (bv. image/jpeg). */
class StoredPhoto(val bytes: ByteArray, val contentType: String)

/**
 * Opslag-poort voor de moestuin-foto's. Fase-0-fallback is [InMemoryPhotoStorage] (ephemeral,
 * verdwijnt bij herstart); een Firebase Storage-implementatie plugt hier later in (zodra
 * Firebase-creds er zijn), zonder de service/controller te raken.
 */
interface PhotoStorage {
    /** Bewaart een foto en geeft een id terug waarmee 'ie later op te halen is. */
    fun store(bytes: ByteArray, contentType: String): String

    fun load(id: String): StoredPhoto?
}

@Component
class InMemoryPhotoStorage : PhotoStorage {
    private val store = ConcurrentHashMap<String, StoredPhoto>()

    override fun store(bytes: ByteArray, contentType: String): String {
        val id = UUID.randomUUID().toString()
        store[id] = StoredPhoto(bytes, contentType)
        return id
    }

    override fun load(id: String): StoredPhoto? = store[id]
}
