package nl.vdzon.robbertsassistent.nutrition

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.push.PushService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MealServiceTest {
    private val item = MealItem("Havermout", 60, 220, proteinG = 8.0, fiberG = 6.0)

    /** Analyzer die een vaste uitkomst geeft en bijhoudt wat hij binnenkreeg. */
    private class FakeAnalyzer(var result: MealAnalysis) : MealAnalyzer {
        val requests = mutableListOf<AnalysisRequest>()
        var learnResult: LearnResult? = null
        var fail = false

        override fun analyze(request: AnalysisRequest): MealAnalysis {
            requests += request
            if (fail) error("kapot")
            return result
        }

        override fun learn(memory: String, meal: Meal): LearnResult? = learnResult
    }

    private val repository = InMemoryMealRepository()
    private val memory = InMemoryFoodMemoryRepository()
    private val analyzer = FakeAnalyzer(MealAnalysis("Ontbijt", "", listOf(item)))
    private val push = Mockito.mock(PushService::class.java)
    private val service = MealService(repository, InMemoryMealPhotoStorage(), memory, analyzer, push)

    private fun awaitStatus(id: String, vararg statuses: MealStatus): Meal {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            val meal = service.get(id)
            if (meal.status in statuses) return meal
            Thread.sleep(10)
        }
        error("Status ${statuses.toList()} niet bereikt, nu ${service.get(id).status}")
    }

    @Test
    fun `nieuw eetmoment wordt een voorstel en stuurt een push`() {
        val created = service.create(listOf(MealPhotoUpload(byteArrayOf(1), "image/jpeg")), "", null)
        assertEquals(MealStatus.ANALYSEREN, created.status)

        val meal = awaitStatus(created.id, MealStatus.VOORSTEL)
        assertEquals("Ontbijt", meal.title)
        assertEquals(listOf(item), meal.items)
        assertEquals(listOf(item), meal.aiProposal)
        assertEquals(1, analyzer.requests.single().photos.size)
        Mockito.verify(push).sendToAll("Ontbijt: 220 kcal", "Havermout", mapOf("type" to "meal", "mealId" to meal.id))
    }

    @Test
    fun `zonder foto en zonder notitie mag niet`() {
        assertFailsWith<IllegalArgumentException> { service.create(emptyList(), "  ", null) }
    }

    @Test
    fun `voorstel overnemen is BEVESTIGD, eigen versie is VERBETERD en bewaart het eerste voorstel`() {
        val first = awaitStatus(service.create(emptyList(), "havermout", null).id, MealStatus.VOORSTEL)
        assertEquals(MealStatus.BEVESTIGD, service.confirm(first.id).status)

        val second = awaitStatus(service.create(emptyList(), "havermout", null).id, MealStatus.VOORSTEL)
        val weighed = item.copy(grams = 75, kcal = 275, basis = ItemBasis.GEWOGEN)
        val confirmed = service.confirm(second.id, listOf(weighed))
        assertEquals(MealStatus.VERBETERD, confirmed.status)
        assertEquals(listOf(weighed), confirmed.items)
        assertEquals(listOf(item), confirmed.aiProposal)
    }

    @Test
    fun `tekstcorrectie rekent opnieuw door met de correctie en het vorige voorstel`() {
        val meal = awaitStatus(service.create(emptyList(), "ontbijt", null).id, MealStatus.VOORSTEL)
        val kwark = MealItem("Kwark", 200, 120)
        analyzer.result = MealAnalysis("Ontbijt", "", listOf(kwark))

        service.correctByText(meal.id, "het was kwark")
        val corrected = awaitStatus(meal.id, MealStatus.VOORSTEL)

        assertEquals(listOf(kwark), corrected.items)
        assertEquals(listOf(item), corrected.aiProposal)
        val request = analyzer.requests.last()
        assertEquals(listOf("het was kwark"), request.corrections)
        assertEquals(listOf(item), request.previous)
        assertEquals(MealStatus.VERBETERD, service.confirm(meal.id).status)
    }

    @Test
    fun `mislukte analyse wordt MISLUKT en kan opnieuw`() {
        analyzer.fail = true
        val meal = awaitStatus(service.create(emptyList(), "iets", null).id, MealStatus.MISLUKT)
        assertTrue(meal.error.isNotEmpty())

        analyzer.fail = false
        service.retry(meal.id)
        assertEquals(MealStatus.VOORSTEL, awaitStatus(meal.id, MealStatus.VOORSTEL).status)
    }

    @Test
    fun `bevestigen werkt het geheugen bij en zet erbij wat geleerd is`() {
        analyzer.learnResult = LearnResult("Ontbijt-havermout: 60 g", "Je ontbijt is 60 g havermout.")
        val meal = awaitStatus(service.create(emptyList(), "ontbijt", null).id, MealStatus.VOORSTEL)
        service.confirm(meal.id)

        val deadline = System.currentTimeMillis() + 5000
        while (service.get(meal.id).learned.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals("Je ontbijt is 60 g havermout.", service.get(meal.id).learned)
        assertEquals("Ontbijt-havermout: 60 g", memory.current())
    }

    @Test
    fun `dagoverzicht telt alleen de maaltijden van die dag in Nederlandse tijd`() {
        // 23:30 Nederlandse wintertijd op 16 november is 22:30 UTC; 00:30 op de 17e is 23:30 UTC de 16e.
        val late = service.create(emptyList(), "laat", Instant.parse("2026-11-16T22:30:00Z"))
        val afterMidnight = service.create(emptyList(), "na middernacht", Instant.parse("2026-11-16T23:30:00Z"))
        awaitStatus(late.id, MealStatus.VOORSTEL)
        awaitStatus(afterMidnight.id, MealStatus.VOORSTEL)

        val day16 = service.day(LocalDate.parse("2026-11-16"))
        val day17 = service.day(LocalDate.parse("2026-11-17"))
        assertEquals(listOf(late.id), day16.meals.map { it.id })
        assertEquals(listOf(afterMidnight.id), day17.meals.map { it.id })
        assertEquals(220, day16.totals.kcal)
        assertEquals(6.0, day16.totals.fiberG)
    }

    @Test
    fun `parser haalt JSON uit een antwoord met tekst en codeblok eromheen`() {
        val answer = """
            Hier is het:
            ```json
            {"titel":"Lunch","opmerking":"twijfel over de kaas",
             "items":[{"naam":"Boterham kaas","gram":70,"kcal":165,"eiwit":9.5,"koolhydraten":15,
                       "vet":7,"vezels":2.4,"groente_gram":0,"fruit_gram":0,"twijfel":true,"basis":"etiket"}]}
            ```
        """.trimIndent()
        val analysis = MealAnalysisParser.parse(answer, ObjectMapper())
        assertEquals("Lunch", analysis.title)
        val parsed = analysis.items.single()
        assertEquals("Boterham kaas", parsed.name)
        assertEquals(165, parsed.kcal)
        assertEquals(2.4, parsed.fiberG)
        assertTrue(parsed.uncertain)
        assertEquals(ItemBasis.ETIKET, parsed.basis)
        assertFailsWith<IllegalArgumentException> { MealAnalysisParser.parse("geen json", ObjectMapper()) }
    }

    @Suppress("unused")
    private val unusedFirebase: FirebaseProvider? = null
}
