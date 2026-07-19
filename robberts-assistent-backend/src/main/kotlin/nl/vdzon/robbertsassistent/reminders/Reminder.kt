package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.scheduling.Recurrence
import java.time.Instant

/**
 * Eén reminder: een bericht dat op [dueAt] een push-notificatie geeft. [recurrence] `null` =
 * eenmalig (na afgaan [active] = false); anders herhalend en schuift [dueAt] telkens door.
 */
data class Reminder(
    val id: String,
    val message: String,
    val dueAt: Instant,
    val recurrence: Recurrence? = null,
    val active: Boolean = true,
)
