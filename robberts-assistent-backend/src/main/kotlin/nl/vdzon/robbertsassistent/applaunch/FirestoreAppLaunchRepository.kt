package nl.vdzon.robbertsassistent.applaunch

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import java.time.Instant

class FirestoreAppLaunchRepository(private val firestore: Firestore) : AppLaunchRepository {
    private val collection get() = firestore.collection(COLLECTION)

    override fun save(launch: AppLaunch): AppLaunch {
        collection.document(launch.id).set(launch.toMap()).get()
        return launch
    }

    override fun recent(limit: Int): List<AppLaunch> =
        collection
            .orderBy(FIELD_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .get()
            .documents
            .mapNotNull { it.toAppLaunch() }

    override fun deleteOlderThan(cutoff: Instant): Int {
        val stale = collection.whereLessThan(FIELD_AT, cutoff.toEpochMilli()).get().get().documents
        stale.forEach { collection.document(it.id).delete().get() }
        return stale.size
    }

    private fun AppLaunch.toMap(): Map<String, Any> = buildMap {
        put(FIELD_AT, at.toEpochMilli())
        put("source", source.name)
        put("platform", platform)
        referrer?.let { put("referrer", it) }
        action?.let { put("action", it) }
        put("categories", categories)
        put("extras", extras)
        appVersion?.let { put("appVersion", it) }
    }

    private fun DocumentSnapshot.toAppLaunch(): AppLaunch? {
        val at = getLong(FIELD_AT)?.let(Instant::ofEpochMilli) ?: return null
        val source = runCatching { AppLaunchSource.valueOf(getString("source").orEmpty()) }
            .getOrDefault(AppLaunchSource.UNKNOWN)
        return AppLaunch(
            id = id,
            at = at,
            source = source,
            platform = getString("platform") ?: "onbekend",
            referrer = getString("referrer"),
            action = getString("action"),
            categories = (get("categories") as? List<*>).orEmpty().map { it.toString() },
            extras = (get("extras") as? Map<*, *>).orEmpty()
                .entries
                .associate { (key, value) -> key.toString() to value.toString() },
            appVersion = getString("appVersion"),
        )
    }

    private companion object {
        const val COLLECTION = "app-launches"
        const val FIELD_AT = "atEpochMillis"
    }
}
