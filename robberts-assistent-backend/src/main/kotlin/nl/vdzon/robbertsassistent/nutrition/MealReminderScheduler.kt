package nl.vdzon.robbertsassistent.nutrition

import java.time.Clock
import java.time.LocalDate
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Eén rustige herinnering per dag: staat er om 20:45 nog geen eetmoment vanaf 16:00, dan gaat er
 * een push uit. Vergeten vast te leggen is de grootste bedreiging voor een eetdagboek.
 */
@Component
class MealReminderScheduler(
    private val meals: MealService,
    private val push: PushService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 45 20 * * *", zone = "Europe/Amsterdam")
    fun remind() {
        runCatching {
            val today = LocalDate.now(clock.withZone(MealService.ZONE))
            if (meals.hasMealSince(today, EVENING_FROM_HOUR)) return
            push.sendToAll(
                "Avondeten nog vastleggen?",
                "Er staat vandaag nog geen avondeten in je eetdagboek.",
                mapOf("type" to "meal"),
            )
        }.onFailure { logger.warn("Avondherinnering versturen mislukt: {}", it.message) }
    }

    private companion object {
        const val EVENING_FROM_HOUR = 16
    }
}
