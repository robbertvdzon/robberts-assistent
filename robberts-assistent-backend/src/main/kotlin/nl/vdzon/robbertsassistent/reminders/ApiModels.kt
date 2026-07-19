package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.scheduling.RecurrenceDto
import nl.vdzon.robbertsassistent.scheduling.toDto

data class ReminderResponse(
    val id: String,
    val message: String,
    // ISO-8601 tijdstip (UTC).
    val dueAt: String,
    val recurrence: RecurrenceDto?,
    val active: Boolean,
)

data class RemindersResponse(val reminders: List<ReminderResponse>)

data class CreateReminderRequest(
    val message: String = "",
    // ISO-8601 tijdstip.
    val dueAt: String = "",
    val recurrence: RecurrenceDto? = null,
)

fun Reminder.toResponse() = ReminderResponse(
    id = id,
    message = message,
    dueAt = dueAt.toString(),
    recurrence = recurrence?.toDto(),
    active = active,
)
