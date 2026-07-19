package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.notifier.Notifier
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Achtergrondagent: kijkt elke minuut welke reminders verstreken zijn en levert ze af als
 * **push-notificatie** (FCM, naar de telefoon) én via de [Notifier] (Telegram/Garmin). Daarna
 * schuift 'ie herhalende reminders door en zet eenmalige op inactief.
 */
@Component
class ReminderScheduler(
    private val remindersService: RemindersService,
    private val pushService: PushService,
    private val notifier: Notifier,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ra.reminders.poll-interval-ms:60000}")
    fun deliverDue() {
        val now = Instant.now()
        val due = remindersService.due(now)
        if (due.isEmpty()) return
        due.forEach { reminder ->
            runCatching { pushService.sendToAll("Reminder", reminder.message) }
                .onFailure { logger.warn("FCM-push van reminder {} faalde: {}", reminder.id, it.message) }
            runCatching { notifier.send("⏰ ${reminder.message}") }
                .onFailure { logger.warn("Telegram-melding van reminder {} faalde: {}", reminder.id, it.message) }
            // Altijd doorschuiven/deactiveren — best-effort aflevering, geen oneindige herhaling.
            remindersService.markFired(reminder, now)
        }
    }
}
