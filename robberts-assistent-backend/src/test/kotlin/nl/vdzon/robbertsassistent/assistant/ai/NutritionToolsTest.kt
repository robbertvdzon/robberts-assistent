package nl.vdzon.robbertsassistent.assistant.ai

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.activity.InMemoryActivityDayRepository
import nl.vdzon.robbertsassistent.insights.InsightService
import nl.vdzon.robbertsassistent.nutrition.AnalysisRequest
import nl.vdzon.robbertsassistent.nutrition.InMemoryFoodMemoryRepository
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealPhotoStorage
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealRepository
import nl.vdzon.robbertsassistent.nutrition.LearnResult
import nl.vdzon.robbertsassistent.nutrition.Meal
import nl.vdzon.robbertsassistent.nutrition.MealAnalysis
import nl.vdzon.robbertsassistent.nutrition.MealAnalyzer
import nl.vdzon.robbertsassistent.nutrition.MealService
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

class NutritionToolsTest {
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
    private val activity = ActivityService(InMemoryActivityDayRepository(), Clock.systemUTC())
    private val insights = InsightService(meals, checkIns, activity, NapService(InMemoryNapRepository()))
    private val tools = NutritionTools(meals, checkIns, insights, activity)

    private fun all(): List<Meal> = mealRepository.findBetween(Instant.EPOCH, Instant.now().plusSeconds(60))

    @Test
    fun `eten achteraf vastleggen zet het op het genoemde tijdstip`() {
        val yesterday = LocalDate.now(MealService.ZONE).minusDays(1)
        val reply = tools.logMeal("yoghurt met havermout", "9:30", yesterday.toString())

        val meal = all().single()
        assertEquals(yesterday.atTime(9, 30).atZone(MealService.ZONE).toInstant(), meal.eatenAt)
        assertEquals("yoghurt met havermout", meal.note)
        assertTrue(reply.contains("09:30"), reply)
    }

    @Test
    fun `zonder tijdstip is het nu, en onzin wordt geweigerd`() {
        tools.logMeal("een appel", null, null)
        assertTrue(Duration.between(all().single().eatenAt, Instant.now()).abs() < Duration.ofMinutes(1))

        assertEquals("Ongeldig tijdstip; gebruik uu:mm.", tools.logMeal("een appel", "half tien", null))
        assertEquals("Ongeldige datum; gebruik jjjj-mm-dd.", tools.logMeal("een appel", null, "gisteren"))
        val tomorrow = ZonedDateTime.now(MealService.ZONE).plusDays(1).toLocalDate().toString()
        assertEquals("Dat tijdstip ligt in de toekomst.", tools.logMeal("een appel", "09:00", tomorrow))
        assertEquals(1, all().size)
    }

    @Test
    fun `gewicht vastleggen komt bij de dag te staan`() {
        val today = LocalDate.now(MealService.ZONE)
        assertTrue(tools.logWeight(81.8, null).startsWith("Vastgelegd"))
        assertEquals(81.8, activity.day(today)?.weightKg)
        assertEquals("Gewicht moet tussen 30 en 250 kg liggen", tools.logWeight(8.0, null))
    }
}
