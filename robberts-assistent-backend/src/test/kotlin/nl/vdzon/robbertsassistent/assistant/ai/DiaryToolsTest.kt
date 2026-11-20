package nl.vdzon.robbertsassistent.assistant.ai

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import nl.vdzon.robbertsassistent.activity.ActivityDay
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.activity.InMemoryActivityDayRepository
import nl.vdzon.robbertsassistent.activity.SleepSummary
import nl.vdzon.robbertsassistent.activity.Workout
import nl.vdzon.robbertsassistent.nutrition.AnalysisRequest
import nl.vdzon.robbertsassistent.nutrition.InMemoryFoodMemoryRepository
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealPhotoStorage
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealRepository
import nl.vdzon.robbertsassistent.nutrition.LearnResult
import nl.vdzon.robbertsassistent.nutrition.Meal
import nl.vdzon.robbertsassistent.nutrition.MealAnalysis
import nl.vdzon.robbertsassistent.nutrition.MealAnalyzer
import nl.vdzon.robbertsassistent.nutrition.MealItem
import nl.vdzon.robbertsassistent.nutrition.MealService
import nl.vdzon.robbertsassistent.nutrition.MealStatus
import nl.vdzon.robbertsassistent.nutrition.RecipeFetcher
import nl.vdzon.robbertsassistent.push.PushService
import nl.vdzon.robbertsassistent.wellbeing.CheckInService
import nl.vdzon.robbertsassistent.wellbeing.InMemoryCheckInRepository
import nl.vdzon.robbertsassistent.wellbeing.InMemoryNapRepository
import nl.vdzon.robbertsassistent.wellbeing.Nap
import nl.vdzon.robbertsassistent.wellbeing.NapService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryToolsTest {
    private val zone = MealService.ZONE
    // Gisteren: een hele dag in het verleden, zodat geen tijdstip in de toekomst valt.
    private val day: LocalDate = LocalDate.now(zone).minusDays(1)
    private fun at(hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).atZone(zone).toInstant()

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
    private val napRepository = InMemoryNapRepository()
    private val naps = NapService(napRepository)
    private val activity = ActivityService(InMemoryActivityDayRepository(), Clock.systemUTC())
    private val tools = DiaryTools(meals, checkIns, naps, activity)

    private fun meal(id: String, hour: Int, status: MealStatus = MealStatus.BEVESTIGD) = mealRepository.save(
        Meal(
            id = id, eatenAt = at(hour), title = "Lunch", status = status,
            items = listOf(MealItem("Brood met kaas", 120, 320)), createdAt = at(hour), updatedAt = at(hour),
        ),
    )

    @Test
    fun `tijdlijn zet alles van de dag op volgorde met id's`() {
        activity.sync(
            listOf(
                ActivityDay(
                    date = day, steps = 8430, totalKcal = 2100,
                    sleep = SleepSummary(at(7).minus(Duration.ofHours(8)), at(7), 480),
                    workouts = listOf(Workout(at(8), at(8, 30), "WALKING", 150)),
                ),
            ),
            "test",
        )
        activity.setWeight(day, 81.8)
        meal("aaaa1111-meal", 12)
        napRepository.save(Nap("bbbb2222-nap", at(13, 10), at(13, 40), endEstimated = true))
        checkIns.record(2, 4, note = "na het wandelen", at = at(15, 30))

        val text = tools.getDayTimeline(day.toString())

        assertTrue(text.startsWith("$day: gewicht 81.8 kg, 8430 stappen, verbruikt 2100 kcal"), text)
        val expected = listOf(
            "geslapen tot 07:00 (8 u 00)",
            "08:00 wandelen tot 08:30 (30 min, 150 kcal)",
            "12:00 Lunch: 320 kcal, Brood met kaas [id aaaa1111]",
            "13:10 dutje tot 13:40 (30 min, eind geschat) [id bbbb2222]",
            "15:30 check-in: honger 2 (beetje honger), energie 4, na het wandelen",
        )
        val positions = expected.map { text.indexOf(it).also { index -> assertTrue(index >= 0, "ontbreekt: $it\n$text") } }
        assertEquals(positions.sorted(), positions, text)
        assertEquals("Op 2020-01-01 staat er niets in het dagboek.", tools.getDayTimeline("2020-01-01"))
    }

    @Test
    fun `dutje starten, niet dubbel, en afmelden een kwartier geleden`() {
        assertEquals("Er loopt geen dutje.", tools.endNap(null, null))
        napRepository.save(Nap("open-nap", Instant.now().minus(Duration.ofMinutes(50)), alarmAt = Instant.now().plus(Duration.ofMinutes(10))))
        assertTrue(tools.startNap(null).startsWith("Er loopt al een dutje"))

        val reply = tools.endNap(15, null)

        assertTrue(reply.startsWith("Dutje afgemeld"), reply)
        val done = naps.get("open-nap")
        assertTrue(Duration.between(done.end, Instant.now().minus(Duration.ofMinutes(15))).abs() < Duration.ofMinutes(1))
        assertNull(done.alarmAt)

        assertTrue(tools.startNap(30).contains("wekker om"))
        assertEquals("Een wekker kan tussen 1 en 600 minuten.", tools.startNap(0))
    }

    @Test
    fun `dutje achteraf invullen en verwijderen`() {
        assertTrue(tools.logNap("13:15", "13.45", day.toString()).contains("13:15 dutje tot 13:45 (30 min)"))
        val nap = naps.day(day).single()
        assertEquals("Ongeldig tijdstip; gebruik uu:mm.", tools.logNap("kwart over één", "14:00", null))

        assertTrue(tools.deleteNap("xxxx9999").startsWith("Geen dutje gevonden"))
        assertTrue(tools.deleteNap(nap.id.take(8)).startsWith("Verwijderd"))
        assertTrue(naps.day(day).isEmpty())
    }

    @Test
    fun `eetmoment verzetten, verbeteren en verwijderen op kort id`() {
        meal("cccc3333-meal", 12)
        meal("dddd4444-meal", 18, status = MealStatus.ANALYSEREN)

        assertTrue(tools.moveMeal("cccc3333", "9:30", null).endsWith("om 09:30."))
        assertEquals(at(9, 30), meals.get("cccc3333-meal").eatenAt)
        assertEquals(MealStatus.BEVESTIGD, meals.get("cccc3333-meal").status, "verzetten verandert de status niet")

        val full = meals.get("cccc3333-meal").kcal
        assertTrue(tools.setMealEaten("cccc3333", 75).contains("telt nu voor 75% mee: ${full * 75 / 100} kcal"))
        assertEquals(75, meals.get("cccc3333-meal").eatenPercent)
        assertEquals("Kies een percentage van 1 tot 100", tools.setMealEaten("cccc3333", 0))

        assertTrue(tools.correctMeal("dddd4444", "het was soep").startsWith("Dat eetmoment wordt nog uitgerekend"))
        assertTrue(tools.correctMeal("cccc3333", "het was volkorenbrood").startsWith("Doorgegeven"))
        assertEquals(listOf("het was volkorenbrood"), meals.get("cccc3333-meal").corrections)

        assertTrue(tools.deleteMeal("ccc").startsWith("Geen eetmoment gevonden"), "te kort id")
        assertTrue(tools.deleteMeal("[id cccc3333]".removeSurrounding("[", "]")).startsWith("Verwijderd: Lunch"))
        assertEquals(listOf("dddd4444-meal"), mealRepository.findBetween(at(0), at(23)).map { it.id })
    }
}
