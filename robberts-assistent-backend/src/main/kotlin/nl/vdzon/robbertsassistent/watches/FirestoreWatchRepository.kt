package nl.vdzon.robbertsassistent.watches

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant

/**
 * Firestore-implementatie van [WatchRepository]. Watches in de collectie `watches`,
 * document-id = watch-id. `.get()` blokkeert (de aanroepende service/scheduler is synchroon).
 */
class FirestoreWatchRepository(private val firestore: Firestore) : WatchRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun save(watch: Watch): Watch {
        collection.document(watch.id).set(watch.toMap()).get()
        return watch
    }

    override fun findById(id: String): Watch? =
        collection.document(id).get().get().toWatch()

    override fun all(): List<Watch> =
        collection.get().get().documents.mapNotNull { it.toWatch() }

    override fun activeWatches(): List<Watch> =
        collection.whereEqualTo(FIELD_ACTIVE, true).get().get().documents.mapNotNull { it.toWatch() }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Watch.toMap(): Map<String, Any?> = buildMap {
        put(FIELD_TITLE, title)
        put(FIELD_URL, url)
        put(FIELD_INSTRUCTION, instruction)
        put(FIELD_FREQUENCY, frequency.name)
        put(FIELD_STATUS, status.name)
        put(FIELD_ACTIVE, active)
        put(FIELD_LAST_CHECKED, lastChecked?.toEpochMilli())
        put(FIELD_CREATED_AT, createdAt.toEpochMilli())
        put(FIELD_UPDATED_AT, updatedAt.toEpochMilli())
    }

    private fun DocumentSnapshot.toWatch(): Watch? {
        if (!exists()) return null
        val title = getString(FIELD_TITLE) ?: return null
        val url = getString(FIELD_URL) ?: return null
        val instruction = getString(FIELD_INSTRUCTION) ?: return null
        val frequencyStr = getString(FIELD_FREQUENCY) ?: return null
        val frequency = runCatching { WatchFrequency.valueOf(frequencyStr) }.getOrNull() ?: return null
        val statusStr = getString(FIELD_STATUS) ?: WatchStatus.ONBEKEND.name
        val status = runCatching { WatchStatus.valueOf(statusStr) }.getOrElse { WatchStatus.ONBEKEND }
        val lastCheckedMillis = getLong(FIELD_LAST_CHECKED)
        val createdAtMillis = getLong(FIELD_CREATED_AT) ?: Instant.now().toEpochMilli()
        val updatedAtMillis = getLong(FIELD_UPDATED_AT) ?: Instant.now().toEpochMilli()

        return Watch(
            id = id,
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
            status = status,
            active = getBoolean(FIELD_ACTIVE) ?: true,
            lastChecked = lastCheckedMillis?.let { Instant.ofEpochMilli(it) },
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            updatedAt = Instant.ofEpochMilli(updatedAtMillis),
        )
    }

    private companion object {
        const val COLLECTION = "watches"
        const val FIELD_TITLE = "title"
        const val FIELD_URL = "url"
        const val FIELD_INSTRUCTION = "instruction"
        const val FIELD_FREQUENCY = "frequency"
        const val FIELD_STATUS = "status"
        const val FIELD_ACTIVE = "active"
        const val FIELD_LAST_CHECKED = "lastCheckedEpochMillis"
        const val FIELD_CREATED_AT = "createdAtEpochMillis"
        const val FIELD_UPDATED_AT = "updatedAtEpochMillis"
    }
}
