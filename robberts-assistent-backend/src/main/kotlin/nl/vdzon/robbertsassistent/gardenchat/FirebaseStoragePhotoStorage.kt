package nl.vdzon.robbertsassistent.gardenchat

import com.google.cloud.storage.Bucket
import java.util.UUID

/**
 * Bewaart de moestuin-foto's in Firebase Cloud Storage, onder de map `moestuin/` zodat ze niet
 * tussen bestaande bestanden in de bucket komen. Het id is de bestandsnaam (zonder prefix).
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
        const val PREFIX = "moestuin/"
    }
}
