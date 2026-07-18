package nl.vdzon.robbertsassistent.reminders

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory reminder-opslag (leeg na herstart). De fallback zolang er geen Firestore geconfigureerd
 * is; [ReminderRepositoryConfig] kiest tussen deze en [FirestoreReminderRepository].
 */
class InMemoryReminderRepository : ReminderRepository {
    private val store = ConcurrentHashMap<String, Reminder>()

    override fun save(reminder: Reminder): Reminder {
        store[reminder.id] = reminder
        return reminder
    }

    override fun all(): List<Reminder> = store.values.toList()

    override fun due(now: Instant): List<Reminder> =
        store.values.filter { !it.delivered && !it.dueAt.isAfter(now) }

    override fun markDelivered(id: String) {
        store.computeIfPresent(id) { _, reminder -> reminder.copy(delivered = true) }
    }

    override fun delete(id: String) {
        store.remove(id)
    }
}
