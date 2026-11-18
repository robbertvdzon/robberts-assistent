package nl.vdzon.robbertsassistent.insights

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import nl.vdzon.robbertsassistent.activity.ActivityDay
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.activity.InMemoryActivityDayRepository
import nl.vdzon.robbertsassistent.activity.SleepSummary
import nl.vdzon.robbertsassistent.activity.Workout
import nl.vdzon.robbertsassistent.nutrition.AnalysisRequest
import nl.vdzon.robbertsassistent.nutrition.InMemoryFoodMemoryRepository
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealPhotoStorage
import nl.vdzon.robbertsassistent.nutrition.InMemoryMealRepository
import nl.vdzon.robbertsassistent.nutrition.ItemBasis
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

class InsightServiceTest {
    // Woensdag 18 november 2026, 20:00 Nederlandse tijd.
    private val now = Instant.parse("2026-11-18T19:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val today = LocalDate.parse("2026-11-18")

    private val mealRepository = InMemoryMealRepository()
    private val noAnalyzer = object : MealAnalyzer {
        override fun analyze(request: AnalysisRequest) = MealAnalysis("", "", emptyList())
        override fun learn(memory: String, meal: Meal): LearnResult? = null
    }
    private val noRecipes = object : RecipeFetcher {
        override fun fetch(url: String): String? = null
    }
    private val meals = MealService(
        mealRepository, InMemoryMealPhotoStorage(), InMemoryFoodMemoryRepository(), noAnalyzer,
        Mockito.mock(PushService::class.java), noRecipes,
    )
    private val checkIns = CheckInService(InMemoryCheckInRepository())
    private val activity = ActivityService(InMemoryActivityDayRepository(), clock)
    private val napRepository = InMemoryNapRepository()
    private val naps = NapService(napRepository, clock)
    private val service = InsightService(meals, checkIns, activity, naps, clock)

    /** Tijdstip op een dag in Nederlandse wintertijd (UTC+1). */
    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).toInstant(ZoneOffset.ofHours(1))

    private fun meal(
        date: LocalDate,
        hour: Int,
        title: String,
        kcal: Int,
        proteinG: Double = 10.0,
        basis: ItemBasis = ItemBasis.GESCHAT,
        status: MealStatus = MealStatus.BEVESTIGD,
    ) {
        val t = at(date, hour)
        mealRepository.save(
            Meal(
                id = "$date-$hour-$title", eatenAt = t, title = title, status = status,
                items = listOf(MealItem(title, 100, kcal, proteinG = proteinG, fiberG = 5.0, vegetableG = 50, basis = basis)),
                createdAt = t, updatedAt = t,
            ),
        )
    }

    private fun sleep(date: LocalDate, minutes: Int, workout: Boolean = false, kcal: Int? = null, steps: Int? = null) {
        val end = at(date, 7)
        activity.sync(
            listOf(
                ActivityDay(
                    date = date, steps = steps, totalKcal = kcal,
                    sleep = SleepSummary(end.minusSeconds(minutes * 60L), end, minutes),
                    workouts = if (workout) listOf(Workout(at(date, 19), at(date, 20), "BIKING", 500)) else emptyList(),
                ),
            ),
            "test",
        )
    }

    @Test
    fun `weekoverzicht telt per dag en middelt alleen over dagen met gegevens`() {
        meal(today, 8, "Ontbijt", 400, basis = ItemBasis.GEWOGEN)
        meal(today, 18, "Avondeten", 600, status = MealStatus.VERBETERD)
        meal(today.minusDays(1), 12, "Lunch", 500)
        meal(today.minusDays(1), 13, "Mislukt", 999, status = MealStatus.MISLUKT)
        meal(today.minusDays(7), 12, "Te oud", 999)
        sleep(today, 400, kcal = 2500, steps = 9000)
        activity.setWeight(today.minusDays(30), 82.0)
        activity.setWeight(today, 81.2)

        val week = service.week(today)

        assertEquals(7, week.days.size)
        assertEquals(today.minusDays(6), week.days.first().date)
        val last = week.days.last()
        assertEquals(1000, last.eatenKcal)
        assertEquals(2500, last.burnedKcal)
        assertEquals(9000, last.steps)
        assertEquals(400, last.sleepMinutes)
        assertEquals(81.2, last.weightKg)
        assertEquals(500, week.days[5].eatenKcal)
        assertEquals(0, week.days[0].mealCount)
        // Twee dagen met eten: (1000 + 500) / 2; verbruik alleen bekend voor vandaag.
        assertEquals(750, week.avgEatenKcal)
        assertEquals(2500, week.avgBurnedKcal)
        // Vandaag is nog niet af en gisteren heeft geen verbruik: nog geen balans.
        assertNull(week.avgBalanceKcal)
        sleep(today.minusDays(1), 420, kcal = 2300)
        assertEquals(-1800, service.week(today).avgBalanceKcal)
        // 400 van de 1500 kcal is gewogen; twee van de drie bevestigde maaltijden ongewijzigd.
        assertEquals(mapOf("GEWOGEN" to 26, "GESCHAT" to 73), week.basisShare)
        assertEquals(66, week.unchangedShare)
        assertEquals(listOf(82.0, 81.2), week.weights.map { it.weightKg })
        assertEquals("Gegeten 1000 kcal · verbruikt 2500 kcal · 9000 stappen · eiwit 20 g, vezels 10 g, groente 100 g", service.todayLine())
    }

    @Test
    fun `zonder gegevens zijn er geen bevindingen en geen regel voor vandaag`() {
        assertEquals(emptyList(), service.insights())
        assertNull(service.todayLine())
    }

    @Test
    fun `slaap en energie is duidelijk bij genoeg dagen en nog te weinig bij een paar`() {
        // Acht dagen lang geslapen met energie 4, acht dagen kort met energie 2.
        (1L..8L).forEach { i ->
            sleep(today.minusDays(i), 450)
            checkIns.record(null, 4, at = at(today.minusDays(i), 15))
            sleep(today.minusDays(i + 10), 330)
            checkIns.record(null, 2, at = at(today.minusDays(i + 10), 15))
        }
        // Een nacht van 6,5 uur valt in geen van beide groepen.
        sleep(today.minusDays(25), 390)
        checkIns.record(null, 5, at = at(today.minusDays(25), 15))

        val insight = service.insights().single { it.key == "slaap-energie" }
        assertEquals(InsightStrength.DUIDELIJK, insight.strength)
        assertEquals(listOf(4.0 to 8, 2.0 to 8), insight.groups.map { it.value to it.n })
        assertTrue(insight.text.contains("gemiddeld 4,0 energie (8 keer)"))

        val few = InsightService(meals, CheckInService(InMemoryCheckInRepository()).also {
            it.record(null, 4, at = at(today.minusDays(1), 15))
            it.record(null, 2, at = at(today.minusDays(11), 15))
        }, activity, naps, clock).insights().single { it.key == "slaap-energie" }
        assertEquals(InsightStrength.NOG_TE_WEINIG, few.strength)
    }

    @Test
    fun `slap van de honger telt hoe vaak het lang geleden was sinds de laatste maaltijd`() {
        (1L..5L).forEach { i ->
            meal(today.minusDays(i), 12, "Lunch", 500)
            // Vier keer slap om 17:30 (5,5 uur na de lunch), één keer al om 14:00.
            checkIns.record(4, null, at = at(today.minusDays(i), if (i == 5L) 14 else 17, if (i == 5L) 0 else 30))
            checkIns.record(1, null, at = at(today.minusDays(i), 13))
        }
        val insight = service.insights().single { it.key == "slap-tijd-sinds-eten" }
        assertTrue(insight.text.startsWith("4 van de 5 keer zat er meer dan 4,5 uur"))
        assertEquals(InsightStrength.DUIDELIJK, insight.strength)
        assertEquals(5, insight.groups.first().n)
        assertEquals(1.0, insight.groups.last().value)
    }

    @Test
    fun `hoe lang een maaltijd vol houdt, per soort en uitgesplitst naar eiwit`() {
        (1L..4L).forEach { i ->
            val day = today.minusDays(i)
            // Eiwitrijk ontbijt houdt 4 uur vol, eiwitarme lunch 2 uur.
            meal(day, 8, "Ontbijt", 400, proteinG = 30.0)
            checkIns.record(0, null, at = at(day, 10))
            checkIns.record(1, null, at = at(day, 12))
            meal(day, 13, "Lunch", 400, proteinG = 8.0)
            checkIns.record(2, null, at = at(day, 15))
        }
        val perMeal = service.insights().single { it.key == "vol-per-maaltijd" }
        assertEquals(listOf("Ontbijt" to 4.0, "Lunch" to 2.0), perMeal.groups.map { it.label to it.value })
        assertEquals(InsightStrength.NOG_TE_WEINIG, perMeal.strength)

        val protein = service.insights().single { it.key == "eiwit-vol" }
        assertEquals(listOf(4.0 to 4, 2.0 to 4), protein.groups.map { it.value to it.n })
    }

    @Test
    fun `ochtend na een training en lunch tegen middagenergie`() {
        (1L..3L).forEach { i ->
            val day = today.minusDays(i * 2)
            sleep(day.minusDays(1), 420, workout = true)
            checkIns.record(2, null, at = at(day, 10))
            sleep(day.minusDays(20), 420, workout = false)
            checkIns.record(1, null, at = at(day.minusDays(19), 10))

            meal(day, 12, "Lunch", 900)
            checkIns.record(null, 2, at = at(day, 15))
            meal(day.minusDays(19), 12, "Lunch", 500)
            checkIns.record(null, 4, at = at(day.minusDays(19), 15))
        }
        val insights = service.insights().associateBy { it.key }
        assertEquals(listOf(2.0 to 3, 1.0 to 3), insights.getValue("training-honger").groups.map { it.value to it.n })
        assertEquals(listOf(2.0 to 3, 4.0 to 3), insights.getValue("lunch-middagenergie").groups.map { it.value to it.n })
    }

    @Test
    fun `dutjes tegen energie later op de dag en tegen een korte nacht`() {
        // Zes dagen terug begon het eerste dutje; daarvoor tellen dagen niet mee.
        (1L..6L).forEach { i ->
            val day = today.minusDays(i)
            val napped = i % 2 == 0L
            sleep(day, if (napped) 330 else 450)
            if (napped) napRepository.save(Nap("nap-$i", at(day, 13), at(day, 13, 40)))
            checkIns.record(null, if (napped) 4 else 2, at = at(day, 18))
            // Voor 16:00 telt niet mee.
            checkIns.record(null, 1, at = at(day, 11))
        }
        // Een lopend dutje vandaag en een oude dag zonder dutje doen niet mee.
        napRepository.save(Nap("open", at(today, 13)))
        sleep(today.minusDays(20), 300)
        checkIns.record(null, 1, at = at(today.minusDays(20), 18))

        val insights = service.insights().associateBy { it.key }
        assertEquals(listOf(4.0 to 3, 2.0 to 3), insights.getValue("dutje-avondenergie").groups.map { it.value to it.n })
        assertEquals(listOf(100.0 to 3, 0.0 to 3), insights.getValue("korte-nacht-dutje").groups.map { it.value to it.n })
    }
}
