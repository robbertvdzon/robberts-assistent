package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.notifier.Notifier
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * De "achtergrondagent" die het fundament af maakt: kijkt elke minuut welke reminders due zijn en
 * pusht ze via de [Notifier]. Pas na een succesvolle push wordt een reminder als afgeleverd
 * gemarkeerd, zodat een mislukte push automatisch de volgende tick opnieuw wordt geprobeerd.
 */
@Component
class ReminderScheduler(
    private val remindersService: RemindersService,
    private val notifier: Notifier,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${ra.reminders.poll-interval-ms:60000}")
    fun deliverDue() {
        val due = remindersService.due(Instant.now())
        if (due.isEmpty()) return
        due.forEach { reminder ->
            runCatching { notifier.send(reminder.message) }
                .onSuccess { remindersService.markDelivered(reminder.id) }
                .onFailure { logger.warn("Kon reminder {} niet versturen: {}", reminder.id, it.message) }
        }
    }
}
