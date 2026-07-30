package nl.vdzon.robbertsassistent.watches

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant

/**
 * Firestore-implementatie van [WatchRepository]. Watches in de collectie `watches`, document-id =
 * watch-id. `.get()` blokkeert (de aanroepende service/scheduler is synchroon).
 */
class FirestoreWatchRepository(private val firestore: Firestore) : WatchRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun save(watch: Watch): Watch {
        collection.document(watch.id).set(watch.toMap()).get()
        return watch
    }

    override fun all(): List<Watch> =
        collection.get().get().documents.mapNotNull { it.toWatch() }

    override fun findById(id: String): Watch? = collection.document(id).get().get().toWatch()

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Watch.toMap(): Map<String, Any> = buildMap {
        put(FIELD_TITLE, title)
        put(FIELD_URL, url)
        put(FIELD_INSTRUCTION, instruction)
        put(FIELD_FREQUENCY, frequency.name)
        put(FIELD_NOTIFY_ON_FOUND, notifyOnFound)
        put(FIELD_STATUS, status.name)
        put(FIELD_STATUS_TEXT, statusText)
        put(FIELD_ACTIVE, active)
        lastCheckedAt?.let { put(FIELD_LAST_CHECKED_AT, it.toEpochMilli()) }
    }

    private fun DocumentSnapshot.toWatch(): Watch? {
        if (!exists()) return null
        val title = getString(FIELD_TITLE) ?: return null
        val url = getString(FIELD_URL) ?: return null
        val instruction = getString(FIELD_INSTRUCTION) ?: return null
        val frequency = getString(FIELD_FREQUENCY)?.let {
            runCatching { WatchFrequency.valueOf(it) }.getOrNull()
        } ?: return null
        val status = getString(FIELD_STATUS)?.let {
            runCatching { WatchStatus.valueOf(it) }.getOrNull()
        } ?: WatchStatus.ONBEKEND
        val lastCheckedAtMillis = getLong(FIELD_LAST_CHECKED_AT)
        return Watch(
            id = id,
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
            notifyOnFound = getBoolean(FIELD_NOTIFY_ON_FOUND) ?: true,
            status = status,
            statusText = getString(FIELD_STATUS_TEXT) ?: "",
            active = getBoolean(FIELD_ACTIVE) ?: true,
            lastCheckedAt = lastCheckedAtMillis?.let { Instant.ofEpochMilli(it) },
        )
    }

    private companion object {
        const val COLLECTION = "watches"
        const val FIELD_TITLE = "title"
        const val FIELD_URL = "url"
        const val FIELD_INSTRUCTION = "instruction"
        const val FIELD_FREQUENCY = "frequency"
        const val FIELD_NOTIFY_ON_FOUND = "notifyOnFound"
        const val FIELD_STATUS = "status"
        const val FIELD_STATUS_TEXT = "statusText"
        const val FIELD_ACTIVE = "active"
        const val FIELD_LAST_CHECKED_AT = "lastCheckedAtEpochMillis"
    }
}
