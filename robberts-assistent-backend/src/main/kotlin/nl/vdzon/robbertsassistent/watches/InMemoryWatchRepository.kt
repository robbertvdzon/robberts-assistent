package nl.vdzon.robbertsassistent.watches

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory watch-opslag (leeg na herstart). Fallback zolang er geen Firestore geconfigureerd is;
 * [WatchRepositoryConfig] kiest tussen deze en [FirestoreWatchRepository].
 */
class InMemoryWatchRepository : WatchRepository {
    private val store = ConcurrentHashMap<String, Watch>()

    override fun save(watch: Watch): Watch {
        store[watch.id] = watch
        return watch
    }

    override fun all(): List<Watch> = store.values.toList()

    override fun findById(id: String): Watch? = store[id]

    override fun delete(id: String) {
        store.remove(id)
    }
}
