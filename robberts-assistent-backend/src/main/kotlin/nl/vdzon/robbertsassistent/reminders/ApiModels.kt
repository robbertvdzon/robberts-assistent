package nl.vdzon.robbertsassistent.reminders

data class ReminderResponse(
    val id: String,
    val message: String,
    // ISO-8601 tijdstip (UTC), bv. "2026-07-18T13:45:00Z".
    val dueAt: String,
    val delivered: Boolean,
)

data class RemindersResponse(val reminders: List<ReminderResponse>)

data class CreateReminderRequest(
    val message: String = "",
    // ISO-8601 tijdstip, bv. "2026-07-18T13:45:00Z".
    val dueAt: String = "",
)
