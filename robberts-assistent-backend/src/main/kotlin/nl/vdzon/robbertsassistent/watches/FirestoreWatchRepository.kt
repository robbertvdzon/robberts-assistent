package nl.vdzon.robbertsassistent.watches

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant

class FirestoreWatchRepository(private val firestore: Firestore) : WatchRepository {
    private val collection get() = firestore.collection(COLLECTION)

    override fun save(watch: Watch): Watch {
        collection.document(watch.id).set(watch.toMap()).get()
        return watch
    }

    override fun all(): List<Watch> =
        collection.get().get().documents.mapNotNull { it.toWatch() }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Watch.toMap(): Map<String, Any> = buildMap {
        put("title", title)
        put("url", url)
        put("instruction", instruction)
        put("frequency", frequency.name)
        put("notifyOnFound", notifyOnFound)
        put("status", status.name)
        put("statusDescription", statusDescription)
        lastCheckedAt?.let { put("lastCheckedAtEpochMillis", it.toEpochMilli()) }
        put("active", active)
    }

    private fun DocumentSnapshot.toWatch(): Watch? {
        val title = getString("title") ?: return null
        val url = getString("url") ?: return null
        val instruction = getString("instruction") ?: return null
        val frequency = runCatching { WatchFrequency.valueOf(getString("frequency").orEmpty()) }.getOrNull()
            ?: return null
        val status = runCatching { WatchStatus.valueOf(getString("status").orEmpty()) }
            .getOrDefault(WatchStatus.NOG_NIET_GECONTROLEERD)
        return Watch(
            id = id,
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
            notifyOnFound = getBoolean("notifyOnFound") ?: false,
            status = status,
            statusDescription = getString("statusDescription") ?: "Nog niet gecontroleerd.",
            lastCheckedAt = getLong("lastCheckedAtEpochMillis")?.let(Instant::ofEpochMilli),
            active = getBoolean("active") ?: true,
        )
    }

    private companion object {
        const val COLLECTION = "watches"
    }
}
