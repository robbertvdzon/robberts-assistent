package nl.vdzon.robbertsassistent.reminders

import java.time.Instant

/**
 * Opslag-poort voor reminders. Fase 0 gebruikt [InMemoryReminderRepository]; fase 2 vervangt
 * die door een Firestore-implementatie zonder de service/scheduler te raken.
 */
interface ReminderRepository {
    fun save(reminder: Reminder): Reminder

    fun all(): List<Reminder>

    /** Alle nog niet-afgeleverde reminders waarvan de tijd (op of vóór [now]) verstreken is. */
    fun due(now: Instant): List<Reminder>

    fun markDelivered(id: String)

    fun delete(id: String)
}
