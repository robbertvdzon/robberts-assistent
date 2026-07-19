package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.scheduling.Recurrence
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Beheert reminders: aanmaken (eenmalig of herhalend), opsommen, verwijderen, en (voor de
 * [ReminderScheduler]) de verstreken reminders opvragen + na afgaan doorschuiven/deactiveren.
 */
@Service
class RemindersService(private val repository: ReminderRepository) {

    fun create(message: String, dueAt: Instant, recurrence: Recurrence? = null): Reminder =
        repository.save(
            Reminder(id = UUID.randomUUID().toString(), message = message, dueAt = dueAt, recurrence = recurrence),
        )

    /** Alle reminders, oplopend op tijd. */
    fun list(): List<Reminder> = repository.all().sortedBy { it.dueAt }

    fun delete(id: String) = repository.delete(id)

    fun due(now: Instant): List<Reminder> = repository.due(now)

    /**
     * Na het afgaan: herhalend → [dueAt] naar het eerstvolgende toekomstige tijdstip; eenmalig →
     * [Reminder.active] = false. Slaat gemiste herhalingen over tot ná [now].
     */
    fun markFired(reminder: Reminder, now: Instant) {
        val recurrence = reminder.recurrence
        if (recurrence == null) {
            repository.save(reminder.copy(active = false))
            return
        }
        var next = recurrence.nextAfter(reminder.dueAt)
        while (!next.isAfter(now)) next = recurrence.nextAfter(next)
        repository.save(reminder.copy(dueAt = next))
    }
}
