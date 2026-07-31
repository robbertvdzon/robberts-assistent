package nl.vdzon.robbertsassistent.watches

import java.util.concurrent.ConcurrentHashMap

interface WatchRepository {
    fun save(watch: Watch): Watch

    /**
     * Vervangt de watch alleen wanneer de actuele waarde nog gelijk is aan [expected].
     * Retourneert [updated] voor de winnende schrijver en null bij een conflict of verwijdering.
     */
    fun compareAndSet(expected: Watch, updated: Watch): Watch?

    fun findById(id: String): Watch?
    fun all(): List<Watch>
    fun delete(id: String)
}

class InMemoryWatchRepository : WatchRepository {
    private val watches = ConcurrentHashMap<String, Watch>()

    override fun save(watch: Watch): Watch {
        watches[watch.id] = watch
        return watch
    }

    override fun compareAndSet(expected: Watch, updated: Watch): Watch? {
        require(expected.id == updated.id)
        return if (watches.replace(expected.id, expected, updated)) updated else null
    }

    override fun findById(id: String): Watch? = watches[id]

    override fun all(): List<Watch> = watches.values.toList()

    override fun delete(id: String) {
        watches.remove(id)
    }
}
