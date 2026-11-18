package nl.vdzon.robbertsassistent.insights

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import nl.vdzon.robbertsassistent.activity.ActivityDay
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.activity.InMemoryActivityDayRepository
import nl.vdzon.robbertsassistent.activity.Workout
import nl.vdzon.robbertsassistent.nutrition.AnalysisRequest
import nl.vdzon.robbertsassistent.nutrition.InMemoryFoodMemoryRepository
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealPhotoStorage
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealRepository
import nl.vdzon.robbertsassistent.nutrition.LearnResult
import nl.vdzon.robbertsassistent.nutrition.Meal
import nl.vdzon.robbertsassistent.nutrition.MealAnalysis
import nl.vdzon.robbertsassistent.nutrition.MealAnalyzer
import nl.vdzon.robbertsassistent.nutrition.MealService
import nl.vdzon.robbertsassistent.nutrition.MealStatus
import nl.vdzon.robbertsassistent.nutrition.RecipeFetcher
import nl.vdzon.robbertsassistent.push.PushService
import nl.vdzon.robbertsassistent.wellbeing.CheckInService
import nl.vdzon.robbertsassistent.wellbeing.InMemoryCheckInRepository
import nl.vdzon.robbertsassistent.wellbeing.InMemoryNapRepository
import nl.vdzon.robbertsassistent.wellbeing.NapService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NapEstimatorTest {
    private val now = Instant.parse("2026-11-18T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val mealRepository = InMemoryMealRepository()
    private val meals = MealService(
        mealRepository, InMemoryMealPhotoStorage(), InMemoryFoodMemoryRepository(),
        object : MealAnalyzer {
            override fun analyze(request: AnalysisRequest) = MealAnalysis("", "", emptyList())
            override fun learn(memory: String, meal: Meal): LearnResult? = null
        },
        Mockito.mock(PushService::class.java),
        object : RecipeFetcher {
            override fun fetch(url: String): String? = null
        },
    )
    private val checkIns = CheckInService(InMemoryCheckInRepository())
    private val activity = ActivityService(InMemoryActivityDayRepository(), clock)
    private val naps = NapService(InMemoryNapRepository(), clock)
    private val estimator = NapEstimator(naps, meals, checkIns, activity, clock)

    private fun minutesAgo(minutes: Long): Instant = now.minusSeconds(minutes * 60)

    @Test
    fun `zonder enig teken blijft het dutje lopen`() {
        val nap = naps.create(start = minutesAgo(90))
        assertTrue(estimator.estimate(nap.id, null).open)
    }

    @Test
    fun `eerste stappen bepalen het eind, een eerdere check-in wint`() {
        val nap = naps.create(start = minutesAgo(120))
        assertEquals(minutesAgo(50), estimator.estimate(nap.id, minutesAgo(50)).end)

        // Een check-in in de eerste minuten telt niet, een latere wel.
        checkIns.record(1, 3, at = minutesAgo(119))
        checkIns.record(1, 3, at = minutesAgo(80))
        val estimated = estimator.estimate(nap.id, minutesAgo(50))
        assertEquals(minutesAgo(80), estimated.end)
        assertTrue(estimated.endEstimated)
    }

    @Test
    fun `een wandeling, een foto van eten en de wekker tellen ook`() {
        val walk = naps.create(start = minutesAgo(120))
        activity.sync(
            listOf(ActivityDay(LocalDate.parse("2026-11-18"), workouts = listOf(Workout(minutesAgo(60), minutesAgo(30), "WALKING")))),
            "test",
        )
        assertEquals(minutesAgo(60), estimator.estimate(walk.id, null).end)
        naps.delete(walk.id)

        val photo = naps.create(start = minutesAgo(55))
        // Vastgelegd 40 minuten geleden, maar ingevuld als "gegeten om 12 uur": het vastleggen telt.
        mealRepository.save(
            Meal(id = "m", eatenAt = minutesAgo(300), title = "Lunch", status = MealStatus.VOORSTEL, createdAt = minutesAgo(40), updatedAt = minutesAgo(40)),
        )
        assertEquals(minutesAgo(40), estimator.estimate(photo.id, null).end)
        naps.delete(photo.id)

        val alarm = naps.create(start = minutesAgo(90), alarmAt = minutesAgo(60))
        assertEquals(minutesAgo(60), estimator.estimate(alarm.id, minutesAgo(20)).end)
    }

    @Test
    fun `een dutje dat Robbert zelf afsloot wordt niet geschat`() {
        val nap = naps.create(start = minutesAgo(120), end = minutesAgo(90))
        assertEquals(minutesAgo(90), estimator.estimate(nap.id, minutesAgo(100)).end)
    }
}
