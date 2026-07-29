package nl.vdzon.robbertsassistent.watches

import java.util.concurrent.ConcurrentHashMap

interface WatchRepository {
    fun save(watch: Watch): Watch
    fun all(): List<Watch>
    fun delete(id: String)
}

class InMemoryWatchRepository : WatchRepository {
    private val watches = ConcurrentHashMap<String, Watch>()

    override fun save(watch: Watch): Watch {
        watches[watch.id] = watch
        return watch
    }

    override fun all(): List<Watch> = watches.values.toList()

    override fun delete(id: String) {
        watches.remove(id)
    }
}
