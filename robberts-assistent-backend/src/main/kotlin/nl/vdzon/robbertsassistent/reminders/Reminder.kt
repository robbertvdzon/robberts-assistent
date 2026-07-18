package nl.vdzon.robbertsassistent.reminders

import java.time.Instant

/**
 * Eén reminder: een bericht dat op [dueAt] moet afgaan (push/alarm via de Notifier).
 * [delivered] wordt true zodra de [ReminderScheduler] het bericht succesvol heeft verstuurd,
 * zodat het niet nogmaals afgaat.
 */
data class Reminder(
    val id: String,
    val message: String,
    val dueAt: Instant,
    val delivered: Boolean = false,
)
