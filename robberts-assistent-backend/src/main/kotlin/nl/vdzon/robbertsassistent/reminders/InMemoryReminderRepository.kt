package nl.vdzon.robbertsassistent.reminders

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory reminder-opslag (leeg na herstart). Fallback zolang er geen Firestore geconfigureerd is;
 * [ReminderRepositoryConfig] kiest tussen deze en [FirestoreReminderRepository].
 */
class InMemoryReminderRepository : ReminderRepository {
    private val store = ConcurrentHashMap<String, Reminder>()

    override fun save(reminder: Reminder): Reminder {
        store[reminder.id] = reminder
        return reminder
    }

    override fun all(): List<Reminder> = store.values.toList()

    override fun due(now: Instant): List<Reminder> =
        store.values.filter { it.active && !it.dueAt.isAfter(now) }

    override fun delete(id: String) {
        store.remove(id)
    }
}
