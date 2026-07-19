package nl.vdzon.robbertsassistent.reminders

import java.time.Instant

/**
 * Opslag-poort voor reminders. Firestore-impl in prod, in-memory fallback lokaal/zonder Firebase.
 */
interface ReminderRepository {
    fun save(reminder: Reminder): Reminder

    fun all(): List<Reminder>

    /** Actieve reminders waarvan de tijd (op of vóór [now]) verstreken is. */
    fun due(now: Instant): List<Reminder>

    fun delete(id: String)
}
