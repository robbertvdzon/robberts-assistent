package nl.vdzon.robbertsassistent.assistant

import com.google.cloud.storage.Bucket
import java.util.UUID

/**
 * Bewaart de assistent-gespreksfoto's in Firebase Cloud Storage, onder de map `assistent-chat/`
 * zodat ze niet tussen de moestuin-foto's (`moestuin/`) of andere bestanden in de bucket komen.
 * Het id is de bestandsnaam (zonder prefix).
 */
class FirebaseStoragePhotoStorage(private val bucket: Bucket) : PhotoStorage {

    override fun store(bytes: ByteArray, contentType: String): String {
        val id = UUID.randomUUID().toString()
        bucket.create("$PREFIX$id", bytes, contentType)
        return id
    }

    override fun load(id: String): StoredPhoto? {
        val blob = bucket.get("$PREFIX$id") ?: return null
        return StoredPhoto(blob.getContent(), blob.contentType ?: "application/octet-stream")
    }

    private companion object {
        const val PREFIX = "assistent-chat/"
    }
}
