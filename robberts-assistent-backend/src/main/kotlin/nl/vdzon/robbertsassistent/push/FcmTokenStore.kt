package nl.vdzon.robbertsassistent.push

import com.google.cloud.firestore.Firestore
import java.util.concurrent.ConcurrentHashMap

/**
 * Opslag van FCM-device-tokens waar de app zich mee registreert. Fallback is
 * [InMemoryFcmTokenStore]; met Firebase kiest [PushConfig] de [FirestoreFcmTokenStore].
 */
interface FcmTokenStore {
    fun add(token: String)
    fun all(): List<String>
    fun remove(token: String)
}

class InMemoryFcmTokenStore : FcmTokenStore {
    private val tokens = ConcurrentHashMap.newKeySet<String>()
    override fun add(token: String) { tokens.add(token) }
    override fun all(): List<String> = tokens.toList()
    override fun remove(token: String) { tokens.remove(token) }
}

/** Tokens als documenten in de collectie `fcmTokens` (document-id = token). */
class FirestoreFcmTokenStore(private val firestore: Firestore) : FcmTokenStore {
    private val collection get() = firestore.collection(COLLECTION)

    override fun add(token: String) {
        collection.document(token.docId()).set(mapOf("token" to token)).get()
    }

    override fun all(): List<String> =
        collection.get().get().documents.mapNotNull { it.getString("token") }

    override fun remove(token: String) {
        collection.document(token.docId()).delete().get()
    }

    // Firestore-document-id's mogen geen '/' bevatten; de token zelf (met '/' → '_') is uniek genoeg.
    private fun String.docId(): String = replace("/", "_")

    private companion object {
        const val COLLECTION = "fcmTokens"
    }
}
