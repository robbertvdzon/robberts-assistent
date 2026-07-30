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

    override fun all(): List<Watch> =
        collection.get().get().documents.mapNotNull { it.toWatch() }

    override fun findById(id: String): Watch? =
        collection.document(id).get().get().toWatch()

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Watch.toMap(): Map<String, Any?> = mapOf(
        FIELD_TITLE to title,
        FIELD_URL to url,
        FIELD_INSTRUCTION to instruction,
        FIELD_FREQUENCY to frequency.name,
        FIELD_STATUS to status.name,
        FIELD_STATUS_TEXT to statusText,
        FIELD_LAST_CHECKED to lastChecked?.toEpochMilli(),
        FIELD_ACTIVE to active,
    )

    private fun DocumentSnapshot.toWatch(): Watch? {
        if (!exists()) return null
        val title = getString(FIELD_TITLE) ?: return null
        val url = getString(FIELD_URL) ?: return null
        val instruction = getString(FIELD_INSTRUCTION) ?: return null
        val frequencyStr = getString(FIELD_FREQUENCY) ?: return null
        val frequency = runCatching { WatchFrequency.valueOf(frequencyStr) }.getOrNull() ?: return null
        val statusStr = getString(FIELD_STATUS) ?: WatchStatus.ONBEKEND.name
        val status = runCatching { WatchStatus.valueOf(statusStr) }.getOrElse { WatchStatus.ONBEKEND }
        val statusText = getString(FIELD_STATUS_TEXT)
        val lastCheckedMillis = getLong(FIELD_LAST_CHECKED)
        val lastChecked = lastCheckedMillis?.let { Instant.ofEpochMilli(it) }
        val active = getBoolean(FIELD_ACTIVE) ?: true
        return Watch(
            id = id,
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
            status = status,
            statusText = statusText,
            lastChecked = lastChecked,
            active = active,
        )
    }

    private companion object {
        const val COLLECTION = "watches"
        const val FIELD_TITLE = "title"
        const val FIELD_URL = "url"
        const val FIELD_INSTRUCTION = "instruction"
        const val FIELD_FREQUENCY = "frequency"
        const val FIELD_STATUS = "status"
        const val FIELD_STATUS_TEXT = "statusText"
        const val FIELD_LAST_CHECKED = "lastCheckedEpochMillis"
        const val FIELD_ACTIVE = "active"
    }
}
