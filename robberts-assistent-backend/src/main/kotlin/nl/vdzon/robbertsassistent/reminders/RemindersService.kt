package nl.vdzon.robbertsassistent.reminders

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Beheert reminders: aanmaken, opsommen, verwijderen, en (voor de [ReminderScheduler]) de
 * afgelopen reminders opvragen/markeren. De opslag zit achter [ReminderRepository].
 */
@Service
class RemindersService(private val repository: ReminderRepository) {

    fun create(message: String, dueAt: Instant): Reminder =
        repository.save(Reminder(id = UUID.randomUUID().toString(), message = message, dueAt = dueAt))

    /** Alle reminders, oplopend op tijd (eerst wat het eerst afgaat). */
    fun list(): List<Reminder> = repository.all().sortedBy { it.dueAt }

    fun delete(id: String) = repository.delete(id)

    fun due(now: Instant): List<Reminder> = repository.due(now)

    fun markDelivered(id: String) = repository.markDelivered(id)
}
