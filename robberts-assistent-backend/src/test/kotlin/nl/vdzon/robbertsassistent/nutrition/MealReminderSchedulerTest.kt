package nl.vdzon.robbertsassistent.nutrition

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import nl.vdzon.robbertsassistent.push.PushService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.scheduling.annotation.Scheduled
import kotlin.test.*

class MealReminderSchedulerTest {
    private val repository = InMemoryMealRepository()
    private val photos = InMemoryMealPhotoStorage()
    private val push = mock(PushService::class.java)
    private val analyzer = mock(MealAnalyzer::class.java)
    private val service = MealService(
        repository, photos, InMemoryFoodMemoryRepository(), analyzer, push, mock(RecipeFetcher::class.java),
    )
    private val today = LocalDate.of(2026, 11, 25)
    private val breakfast = MealItem("Havermout", 100, 400, proteinG = 12.0, basis = ItemBasis.GEWOGEN)
    private val lunch = MealItem("Boterham met kaas", 150, 350)

    @AfterEach
    fun close() = service.shutdown()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Instant =
        date.atTime(hour, minute).atZone(MealService.ZONE).toInstant()

    private fun seed(
        id: String,
        date: LocalDate = today.minusDays(1),
        hour: Int = 8,
        item: MealItem = breakfast,
        status: MealStatus = MealStatus.BEVESTIGD,
        automaticSource: String = "",
        percent: Int = 100,
    ): Meal = repository.save(
        Meal(
            id = id, eatenAt = at(date, hour), title = item.name, items = listOf(item),
            status = status, automaticSourceMealId = automaticSource, eatenPercent = percent,
            createdAt = at(date, hour), updatedAt = at(date, hour),
        ),
    )

    private fun scheduler(date: LocalDate = today, hour: Int = 12): MealReminderScheduler =
        MealReminderScheduler(service, push, Clock.fixed(at(date, hour), MealService.ZONE))

    @Test
    fun `ontbijt neemt meest gegeten gerecht met laatste portie over en meldt wissen`() {
        seed("oud", date = today.minusDays(3))
        val source = seed("recent", date = today.minusDays(2), item = breakfast.copy(name = "  HAVERMOUT  "), percent = 50)
        seed("ander-ontbijt", item = MealItem("Yoghurt", 200, 120))
        seed("geen-ontbijt", hour = 13, item = lunch)

        scheduler().fillBreakfast()

        val meal = service.day(today).meals.single()
        assertEquals(MealStatus.VOORSTEL, meal.status)
        assertEquals(source.id, meal.automaticSourceMealId)
        assertEquals(at(today, 8), meal.eatenAt)
        assertEquals(200, meal.kcal)
        assertEquals(6.0, meal.eatenItems.single().proteinG)
        assertEquals(100, meal.eatenPercent)
        assertEquals(ItemBasis.GESCHAT, meal.items.single().basis)
        assertTrue(meal.items.single().uncertain)
        assertEquals(meal.items, meal.aiProposal)
        assertTrue(meal.photoIds.isEmpty())
        assertEquals(ReferenceRole.GEEN, meal.referenceRole)
        assertTrue(meal.remark.contains("automatisch"))
        verify(push).sendToAll(
            "Ontbijt automatisch ingevuld",
            "${source.title}: 200 kcal. Je kunt deze invoer wissen als je echt niet hebt ontbeten.",
            mapOf("type" to "meal", "mealId" to meal.id),
        )
        verifyNoInteractions(analyzer)
        assertEquals(200, service.day(today).totals.kcal)
    }

    @Test
    fun `lunch om 17 uur kijkt alleen naar lunch en telt niet als avondeten`() {
        seed("ontbijt")
        seed("lunch", hour = 16, item = lunch)
        seed("avondeten", hour = 18, item = MealItem("Pasta", 400, 600))
        seed("vandaag-ontbijt", date = today)

        scheduler(hour = 17).fillLunch()

        val meal = service.day(today).meals.last()
        assertEquals("lunch", meal.automaticSourceMealId)
        assertEquals(at(today, 16), meal.eatenAt)
        verify(push).sendToAll(
            "Lunch automatisch ingevuld",
            "Boterham met kaas: 350 kcal. Je kunt deze invoer wissen als je echt niet hebt geluncht.",
            mapOf("type" to "meal", "mealId" to meal.id),
        )
        assertFalse(service.hasMealSince(today, 16))
        scheduler(hour = 20).remind()
        verify(push).sendToAll("Avondeten nog vastleggen?", "Er staat vandaag nog geen avondeten in je eetdagboek.", mapOf("type" to "meal"))
    }

    @Test
    fun `bestaande invoer inclusief lopende analyse voorkomt automatische maaltijd en popup`() {
        seed("voorbeeld")
        seed("vandaag", date = today, status = MealStatus.ANALYSEREN)
        seed("lunch", hour = 13, item = lunch)
        seed("vandaag-lunch", date = today, hour = 12, status = MealStatus.MISLUKT)

        scheduler().fillBreakfast()
        scheduler(hour = 17).fillLunch()

        assertEquals(2, service.day(today).meals.size)
        verifyNoInteractions(push)
    }

    @Test
    fun `geen bruikbare historie geeft alleen herinnering en verzint geen maaltijd`() {
        seed("te-oud", date = today.minusDays(61))
        seed("mislukt", status = MealStatus.MISLUKT)
        seed("bezig", status = MealStatus.ANALYSEREN)
        seed("automatisch", automaticSource = "voorbeeld")
        seed("leeg").also { repository.save(it.copy(items = emptyList())) }

        scheduler().fillBreakfast()

        assertTrue(service.day(today).meals.isEmpty())
        verify(push).sendToAll(
            "Ontbijt nog vastleggen?",
            "Er is nog geen gebruikelijke maaltijd bekend. Vul je ontbijt in als je hebt gegeten.",
            mapOf("type" to "meal"),
        )
    }

    @Test
    fun `automatische maaltijden beinvloeden frequentie niet en nieuwste gerecht wint gelijkspel`() {
        seed("havermout")
        seed("kwark", item = MealItem("Kwark", 200, 150), date = today.minusDays(2))
        repeat(4) { seed("auto-$it", item = MealItem("Kwark", 200, 150), automaticSource = "kwark") }

        scheduler().fillBreakfast()
        assertEquals("havermout", service.day(today).meals.single().automaticSourceMealId)
    }

    @Test
    fun `herhaald uitvoeren maakt en meldt slechts eenmaal ook na verplaatsen`() {
        seed("voorbeeld")
        val scheduler = scheduler()
        scheduler.fillBreakfast()
        val meal = service.day(today).meals.single()
        repository.save(meal.copy(eatenAt = at(today.minusDays(1), 8)))
        scheduler.fillBreakfast()

        verify(push, times(1)).sendToAll(anyString(), anyString(), anyMap())
        assertTrue(service.day(today).meals.isEmpty())
    }

    @Test
    fun `automatische invoer is te verwijderen zonder voorbeeld of foto te verwijderen`() {
        val photo = photos.store(byteArrayOf(1), "image/jpeg")
        val source = seed("voorbeeld").copy(photoIds = listOf(photo))
        repository.save(source)
        scheduler().fillBreakfast()
        service.delete(service.day(today).meals.single().id)

        assertTrue(service.day(today).meals.isEmpty())
        assertEquals(source, repository.findById(source.id))
        assertNotNull(photos.load(photo))
    }

    @ParameterizedTest
    @ValueSource(strings = ["2026-07-01", "2026-11-25"])
    fun `lokale dag en tijd blijven goed in zomer en winter`(dateText: String) {
        val date = LocalDate.parse(dateText)
        seed("voorbeeld", date = date.minusDays(1), hour = 7)
        scheduler(date).fillBreakfast()
        assertEquals(at(date, 7), service.day(date).meals.single().eatenAt)
    }

    @Test
    fun `schedules gebruiken 12 en 17 uur Nederlandse tijd`() {
        for ((method, cron) in listOf("fillBreakfast" to "0 0 12 * * *", "fillLunch" to "0 0 17 * * *")) {
            val schedule = MealReminderScheduler::class.java.getMethod(method).getAnnotation(Scheduled::class.java)
            assertEquals(cron, schedule.cron)
            assertEquals("Europe/Amsterdam", schedule.zone)
        }
    }
}
