package nl.vdzon.robbertsassistent.nutrition

import jakarta.annotation.PreDestroy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

class MealPhotoUpload(val bytes: ByteArray, val contentType: String)

class MealNotFoundException(id: String) : RuntimeException("Maaltijd $id niet gevonden")

/** Eén dag eten: de maaltijden in volgorde en de opgetelde waarden. */
data class NutritionDay(val date: LocalDate, val meals: List<Meal>, val totals: NutritionTotals)

/**
 * Kern van het eetdagboek. Een nieuw eetmoment wordt direct opgeslagen met status ANALYSEREN; de
 * AI-analyse loopt op de achtergrond (de Agent Runtime doet er tientallen seconden over) en zet
 * de maaltijd daarna op VOORSTEL, met een push. De app pollt of reageert op de push — zelfde
 * gedachte als `assistant.AssistantChatJobs`, maar de status staat hier in de opslag zelf.
 */
@Service
class MealService(
    private val meals: MealRepository,
    private val photos: MealPhotoStorage,
    private val memory: FoodMemoryRepository,
    private val analyzer: MealAnalyzer,
    private val push: PushService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "meal-analysis").apply { isDaemon = true }
    }

    fun create(uploads: List<MealPhotoUpload>, note: String, eatenAt: Instant?): Meal {
        require(uploads.isNotEmpty() || note.isNotBlank()) { "Stuur minstens één foto of een notitie" }
        val now = Instant.now()
        val meal = meals.save(
            Meal(
                id = UUID.randomUUID().toString(),
                eatenAt = eatenAt ?: now,
                photoIds = uploads.map { photos.store(it.bytes, it.contentType) },
                note = note.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        startAnalysis(meal.id, notify = true)
        return meal
    }

    /** Extra foto's (tweede bord, etiket, recept) en/of notitie bij een bestaand eetmoment. */
    fun addPhotos(id: String, uploads: List<MealPhotoUpload>, note: String): Meal {
        val meal = get(id)
        val updated = meals.save(
            meal.copy(
                photoIds = meal.photoIds + uploads.map { photos.store(it.bytes, it.contentType) },
                note = listOf(meal.note, note.trim()).filter { it.isNotEmpty() }.joinToString("\n"),
                status = MealStatus.ANALYSEREN,
                error = "",
                updatedAt = Instant.now(),
            ),
        )
        startAnalysis(id, notify = false)
        return updated
    }

    /** Verbetering in gewone woorden: de AI rekent het eetmoment opnieuw door. */
    fun correctByText(id: String, text: String): Meal {
        require(text.isNotBlank()) { "Geef aan wat er niet klopt" }
        val meal = get(id)
        val updated = meals.save(
            meal.copy(
                corrections = meal.corrections + text.trim(),
                status = MealStatus.ANALYSEREN,
                error = "",
                updatedAt = Instant.now(),
            ),
        )
        startAnalysis(id, notify = false)
        return updated
    }

    fun retry(id: String): Meal {
        val meal = get(id)
        val updated = meals.save(meal.copy(status = MealStatus.ANALYSEREN, error = "", updatedAt = Instant.now()))
        startAnalysis(id, notify = false)
        return updated
    }

    /**
     * Bevestigt het eetmoment. Met [items] slaat de gebruiker zijn eigen versie op; zonder neemt
     * hij het voorstel over. De status volgt uit de vergelijking met het eerste AI-voorstel.
     */
    fun confirm(id: String, items: List<MealItem>? = null, title: String? = null, eatenAt: Instant? = null): Meal {
        val meal = get(id)
        check(meal.status != MealStatus.ANALYSEREN) { "De analyse loopt nog" }
        val finalItems = items ?: meal.items
        val confirmed = meals.save(
            meal.copy(
                items = finalItems,
                title = title?.trim()?.takeIf { it.isNotEmpty() } ?: meal.title,
                eatenAt = eatenAt ?: meal.eatenAt,
                status = if (finalItems == meal.aiProposal) MealStatus.BEVESTIGD else MealStatus.VERBETERD,
                error = "",
                updatedAt = Instant.now(),
            ),
        )
        executor.submit { learnFrom(confirmed.id) }
        return confirmed
    }

    fun get(id: String): Meal = expireIfStuck(meals.findById(id) ?: throw MealNotFoundException(id))

    fun delete(id: String) {
        val meal = meals.findById(id) ?: return
        meals.delete(id)
        meal.photoIds.forEach { photoId -> runCatching { photos.delete(photoId) } }
    }

    fun photo(id: String): StoredMealPhoto? = photos.load(id)

    fun day(date: LocalDate): NutritionDay {
        val from = date.atStartOfDay(ZONE).toInstant()
        val to = date.plusDays(1).atStartOfDay(ZONE).toInstant()
        val dayMeals = meals.findBetween(from, to).map(::expireIfStuck)
        return NutritionDay(date, dayMeals, NutritionTotals.of(dayMeals))
    }

    fun memoryText(): String = memory.current()
    fun updateMemory(text: String): String = memory.update(text.trim())

    // ---- achtergrondwerk ----------------------------------------------------------------------

    private fun startAnalysis(id: String, notify: Boolean) {
        executor.submit {
            runCatching { analyse(id, notify) }.onFailure { e ->
                logger.warn("Analyse van maaltijd {} mislukt", id, e)
                meals.findById(id)?.let {
                    meals.save(it.copy(status = MealStatus.MISLUKT, error = e.message ?: e.javaClass.simpleName, updatedAt = Instant.now()))
                }
            }
        }
    }

    private fun analyse(id: String, notify: Boolean) {
        val meal = meals.findById(id) ?: return
        val analysis = analyzer.analyze(
            AnalysisRequest(
                photos = meal.photoIds.mapNotNull { photos.load(it) },
                note = meal.note,
                eatenAtLocal = meal.eatenAt.atZone(ZONE).toLocalDateTime(),
                memory = memory.current(),
                recent = recentExamples(meal),
                previous = meal.items,
                corrections = meal.corrections,
            ),
        )
        // Opnieuw lezen: tijdens de analyse kan de maaltijd verwijderd of aangevuld zijn.
        val current = meals.findById(id) ?: return
        if (current.updatedAt != meal.updatedAt) {
            // Er kwam intussen een foto of correctie bij; die heeft zijn eigen analyse gestart.
            return
        }
        val saved = meals.save(
            current.copy(
                title = analysis.title.ifEmpty { current.title.ifEmpty { "Eetmoment" } },
                remark = analysis.remark,
                items = analysis.items,
                aiProposal = current.aiProposal.ifEmpty { analysis.items },
                status = MealStatus.VOORSTEL,
                error = "",
                updatedAt = Instant.now(),
            ),
        )
        if (notify) {
            runCatching {
                push.sendToAll(
                    title = "${saved.title}: ${saved.kcal} kcal",
                    body = saved.items.joinToString(", ") { it.name }.take(140).ifEmpty { "Tik om te bekijken" },
                    data = mapOf("type" to "meal", "mealId" to saved.id),
                )
            }.onFailure { logger.warn("Push voor maaltijd {} mislukt: {}", id, it.message) }
        }
    }

    private fun learnFrom(id: String) {
        runCatching {
            val meal = meals.findById(id) ?: return
            val result = analyzer.learn(memory.current(), meal) ?: return
            memory.update(result.memory)
            meals.findById(id)?.let { meals.save(it.copy(learned = result.learned)) }
        }.onFailure { logger.warn("Leren van maaltijd {} mislukt: {}", id, it.message) }
    }

    /** De laatste bevestigde maaltijden van de afgelopen twee weken, als voorbeeld voor de AI. */
    private fun recentExamples(meal: Meal): List<Meal> =
        meals.findBetween(meal.eatenAt.minus(Duration.ofDays(14)), meal.eatenAt)
            .filter { it.id != meal.id && (it.status == MealStatus.BEVESTIGD || it.status == MealStatus.VERBETERD) }
            .takeLast(MAX_EXAMPLES)

    /** Na een pod-herstart is een lopende analyse weg; markeer zo'n maaltijd als mislukt. */
    private fun expireIfStuck(meal: Meal): Meal =
        if (meal.status == MealStatus.ANALYSEREN && meal.updatedAt.isBefore(Instant.now().minus(STUCK_AFTER))) {
            meals.save(meal.copy(status = MealStatus.MISLUKT, error = "De analyse is afgebroken", updatedAt = Instant.now()))
        } else {
            meal
        }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        private val STUCK_AFTER: Duration = Duration.ofMinutes(10)
        private const val MAX_EXAMPLES = 20
    }
}
