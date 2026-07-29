package nl.vdzon.robbertsassistent.watches

/**
 * Opslag-poort voor zoekopdrachten. Firestore-impl in prod, in-memory fallback lokaal/zonder
 * Firebase (zelfde patroon als `reminders.ReminderRepository`).
 */
interface WatchRepository {
    fun save(watch: Watch): Watch

    fun all(): List<Watch>

    fun find(id: String): Watch?

    fun delete(id: String)
}
