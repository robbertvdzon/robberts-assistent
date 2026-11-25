package nl.vdzon.robbertsassistent.nutrition

import java.time.Clock
import java.time.LocalDate
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Ontbrekend ontbijt/lunch automatisch invullen met een popup; 's avonds alleen herinneren.
 */
@Component
class MealReminderScheduler(
    private val meals: MealService,
    private val push: PushService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Amsterdam")
    fun fillBreakfast() = fillMissing(0, 12, "Ontbijt", "ontbeten")

    @Scheduled(cron = "0 0 17 * * *", zone = "Europe/Amsterdam")
    fun fillLunch() = fillMissing(12, 17, "Lunch", "geluncht")

    private fun fillMissing(fromHour: Int, toHour: Int, label: String, eaten: String) {
        runCatching {
            val today = LocalDate.now(clock.withZone(MealService.ZONE))
            when (val result = meals.fillMissingMeal(today, fromHour, toHour, label, clock.instant())) {
                MissingMealResult.AlreadyPresent -> return
                MissingMealResult.NoHistory -> push.sendToAll(
                    "$label nog vastleggen?",
                    "Er is nog geen gebruikelijke maaltijd bekend. Vul je ${label.lowercase()} in als je hebt gegeten.",
                    mapOf("type" to "meal"),
                )
                is MissingMealResult.Created -> push.sendToAll(
                    "$label automatisch ingevuld",
                    "${result.meal.title}: ${result.meal.kcal} kcal. Je kunt deze invoer wissen als je echt niet hebt $eaten.",
                    mapOf("type" to "meal", "mealId" to result.meal.id),
                )
            }
        }.onFailure { logger.warn("{} automatisch invullen mislukt: {}", label, it.message) }
    }

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
