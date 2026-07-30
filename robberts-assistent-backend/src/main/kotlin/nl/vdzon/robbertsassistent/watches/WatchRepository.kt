package nl.vdzon.robbertsassistent.watches

/** Opslag-poort voor watches. Firestore-impl in prod, in-memory fallback lokaal/zonder Firebase. */
interface WatchRepository {
    fun save(watch: Watch): Watch

    fun all(): List<Watch>

    fun findById(id: String): Watch?

    fun delete(id: String)
}
