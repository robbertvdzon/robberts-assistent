package nl.vdzon.robbertsassistent.watches

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant

/**
 * Firestore-implementatie van [WatchRepository]. Zoekopdrachten in de collectie `watches`,
 * document-id = watch-id. `.get()` blokkeert (de aanroepende service/scheduler is synchroon).
 */
class FirestoreWatchRepository(private val firestore: Firestore) : WatchRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun save(watch: Watch): Watch {
        collection.document(watch.id).set(watch.toMap()).get()
        return watch
    }

    override fun all(): List<Watch> =
        collection.get().get().documents.mapNotNull { it.toWatch() }

    override fun find(id: String): Watch? = collection.document(id).get().get().toWatch()

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Watch.toMap(): Map<String, Any> = buildMap {
        put(FIELD_TITLE, title)
        put(FIELD_URL, url)
        put(FIELD_INSTRUCTION, instruction)
        put(FIELD_FREQUENCY, frequency.name)
        put(FIELD_PUSH_ON_FOUND, pushOnFound)
        put(FIELD_ACTIVE, active)
        put(FIELD_FOUND, found)
        put(FIELD_CREATED_AT, createdAt.toEpochMilli())
        lastCheckedAt?.let { put(FIELD_LAST_CHECKED_AT, it.toEpochMilli()) }
        lastStatus?.let { put(FIELD_LAST_STATUS, it) }
        lastError?.let { put(FIELD_LAST_ERROR, it) }
    }

    private fun DocumentSnapshot.toWatch(): Watch? {
        val title = getString(FIELD_TITLE) ?: return null
        val url = getString(FIELD_URL) ?: return null
        return Watch(
            id = id,
            title = title,
            url = url,
            instruction = getString(FIELD_INSTRUCTION).orEmpty(),
            frequency = WatchFrequency.fromName(getString(FIELD_FREQUENCY)) ?: WatchFrequency.DAGELIJKS,
            pushOnFound = getBoolean(FIELD_PUSH_ON_FOUND) ?: true,
            active = getBoolean(FIELD_ACTIVE) ?: true,
            lastCheckedAt = getLong(FIELD_LAST_CHECKED_AT)?.let { Instant.ofEpochMilli(it) },
            lastStatus = getString(FIELD_LAST_STATUS),
            found = getBoolean(FIELD_FOUND) ?: false,
            lastError = getString(FIELD_LAST_ERROR),
            createdAt = Instant.ofEpochMilli(getLong(FIELD_CREATED_AT) ?: 0L),
        )
    }

    private companion object {
        const val COLLECTION = "watches"
        const val FIELD_TITLE = "title"
        const val FIELD_URL = "url"
        const val FIELD_INSTRUCTION = "instruction"
        const val FIELD_FREQUENCY = "frequency"
        const val FIELD_PUSH_ON_FOUND = "pushOnFound"
        const val FIELD_ACTIVE = "active"
        const val FIELD_FOUND = "found"
        const val FIELD_LAST_CHECKED_AT = "lastCheckedAtEpochMillis"
        const val FIELD_LAST_STATUS = "lastStatus"
        const val FIELD_LAST_ERROR = "lastError"
        const val FIELD_CREATED_AT = "createdAtEpochMillis"
    }
}
