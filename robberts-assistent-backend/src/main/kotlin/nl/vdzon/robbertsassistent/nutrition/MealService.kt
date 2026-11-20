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
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
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
    private val recipeFetcher: RecipeFetcher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "meal-analysis").apply { isDaemon = true }
    }

    /** Eén tegelijk: twee gelijke maaltijden mogen niet allebei "nieuw" worden. */
    private val catalogueExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "meal-reference").apply { isDaemon = true }
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
        // Andere onderdelen betekent een andere referentie: dan opnieuw beoordelen.
        val keepReference = finalItems == meal.items && meal.referenceRole != ReferenceRole.ONBEKEND
        val confirmed = meals.save(
            (if (keepReference) meal else meal.withoutReference()).copy(
                items = finalItems,
                title = title?.trim()?.takeIf { it.isNotEmpty() } ?: meal.title,
                eatenAt = eatenAt ?: meal.eatenAt,
                status = if (finalItems == meal.aiProposal) MealStatus.BEVESTIGD else MealStatus.VERBETERD,
                error = "",
                updatedAt = Instant.now(),
            ),
        )
        executor.submit { learnFrom(confirmed.id) }
        if (!keepReference) catalogueExecutor.submit { catalogue(confirmed.id) }
        return confirmed
    }

    /** Zet het eetmoment op een ander tijdstip; de rest (status, onderdelen) blijft zoals het was. */
    fun move(id: String, eatenAt: Instant): Meal {
        require(!eatenAt.isAfter(Instant.now().plusSeconds(300))) { "Dat tijdstip ligt in de toekomst" }
        return meals.save(get(id).copy(eatenAt = eatenAt, updatedAt = Instant.now()))
    }

    /** Legt vast welk deel van de portie is opgegeten; de portie zelf (en de referentie) blijft heel. */
    fun setEaten(id: String, percent: Int): Meal {
        require(percent in 1..100) { "Kies een percentage van 1 tot 100" }
        return meals.save(get(id).copy(eatenPercent = percent))
    }

    fun get(id: String): Meal = expireIfStuck(meals.findById(id) ?: throw MealNotFoundException(id))

    fun delete(id: String) {
        val meal = meals.findById(id) ?: return
        meals.delete(id)
        meal.photoIds.forEach { photoId -> runCatching { photos.delete(photoId) } }
        if (meal.referenceRole == ReferenceRole.REFERENTIE) {
            // De dubbelen van deze referentie hebben geen origineel meer: één ervan neemt de plek over.
            val orphans = meals.findBetween(meal.eatenAt.minus(LIBRARY_WINDOW), Instant.now().plus(Duration.ofDays(1)))
                .filter { it.duplicateOf == id }
            orphans.forEach { meals.save(it.withoutReference()) }
            orphans.forEach { orphan -> catalogueExecutor.submit { catalogue(orphan.id) } }
        }
    }

    /**
     * Beoordeelt bij de start de bevestigde maaltijden met foto die nog geen plek in de
     * referentiebibliotheek hebben (van vóór de bibliotheek, of waarvan de beoordeling mislukte).
     */
    @EventListener(ApplicationReadyEvent::class)
    fun catalogueBacklog() {
        catalogueExecutor.submit {
            runCatching {
                libraryWindow(Instant.now())
                    .filter { it.isConfirmed() && it.photoIds.isNotEmpty() && it.referenceRole == ReferenceRole.ONBEKEND }
                    .forEach { catalogue(it.id) }
            }.onFailure { logger.warn("Referentiebibliotheek bijwerken mislukt: {}", it.message) }
        }
    }

    fun photo(id: String): StoredMealPhoto? = photos.load(id)

    fun day(date: LocalDate): NutritionDay {
        val from = date.atStartOfDay(ZONE).toInstant()
        val to = date.plusDays(1).atStartOfDay(ZONE).toInstant()
        val dayMeals = meals.findBetween(from, to).map(::expireIfStuck)
        return NutritionDay(date, dayMeals, NutritionTotals.of(dayMeals))
    }

    /** Maaltijden met `from <= eatenAt < to`, oplopend; voor het weekoverzicht en de verbanden. */
    fun between(from: Instant, to: Instant): List<Meal> = meals.findBetween(from, to)

    /** Of er vandaag vanaf [fromHour] uur (Nederlandse tijd) al iets is vastgelegd. */
    fun hasMealSince(date: LocalDate, fromHour: Int): Boolean =
        meals.findBetween(date.atTime(fromHour, 0).atZone(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant()).isNotEmpty()

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
        val weighed = weighedMeals(meal)
        val analysis = analyzer.analyze(
            AnalysisRequest(
                // Zolang de bibliotheek leeg is, vallen we terug op de gewogen maaltijden.
                references = library(meal, meal.photoIds.sumOf { photos.load(it)?.bytes?.size ?: 0 }).ifEmpty { references(meal, weighed) },
                weighedPortions = weighedPortions(weighed),
                recipes = RecipeLinks.find(meal.note)
                    .mapNotNull { url -> recipeFetcher.fetch(url)?.let { url to it } }.toMap(),
                photos = meal.photoIds.mapNotNull { photos.load(it) },
                note = meal.note,
                eatenAtLocal = meal.eatenAt.atZone(ZONE).toLocalDateTime(),
                memory = memory.current(),
                recent = recentExamples(meal),
                previous = meal.items,
                corrections = meal.corrections,
            ),
        )
        // Een voorstel zonder dat de foto bekeken is, is erger dan geen voorstel: dan zou er een
        // verzonnen portie in het dagboek komen. Zo'n antwoord telt als mislukt en is opnieuw te proberen.
        check(!(meal.photoIds.isNotEmpty() && analysis.remark.startsWith(PHOTO_NOT_READ))) {
            "De foto kon niet worden bekeken. Probeer het opnieuw."
        }
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
                eatenPercent = analysis.eatenPercent ?: current.eatenPercent,
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

    /** Geeft een bevestigde maaltijd zijn plek in de referentiebibliotheek: nieuw, dubbel of geen. */
    private fun catalogue(id: String) {
        runCatching {
            val meal = meals.findById(id) ?: return
            if (!meal.isConfirmed() || meal.referenceRole != ReferenceRole.ONBEKEND) return
            val loaded = meal.photoIds.mapNotNull { photoId -> photos.load(photoId)?.let { photoId to it } }
            if (loaded.isEmpty()) {
                meals.save(meal.copy(referenceRole = ReferenceRole.GEEN))
                return
            }
            val existing = libraryWindow(maxOf(meal.eatenAt, Instant.now()))
                .filter { it.id != id && it.referenceRole == ReferenceRole.REFERENTIE }
            val verdict = analyzer.catalogue(meal, loaded.map { it.second }, existing) ?: return
            // Opnieuw lezen: tijdens de beoordeling kan de maaltijd veranderd of verwijderd zijn.
            val current = meals.findById(id) ?: return
            if (current.updatedAt != meal.updatedAt) return
            meals.save(
                current.copy(
                    referenceRole = verdict.role,
                    referenceDescription = verdict.description,
                    referencePhotoId = if (verdict.role == ReferenceRole.REFERENTIE) loaded[verdict.photoIndex.coerceIn(loaded.indices)].first else "",
                    duplicateOf = verdict.duplicateOf,
                ),
            )
            logger.info("Maaltijd {} ({}) in de referentiebibliotheek: {}", id.take(8), meal.title, verdict.role)
        }.onFailure { logger.warn("Referentie beoordelen van maaltijd {} mislukt: {}", id, it.message) }
    }

    private fun libraryWindow(until: Instant): List<Meal> =
        meals.findBetween(until.minus(LIBRARY_WINDOW), until.plus(Duration.ofDays(1)))

    /**
     * De referentiebibliotheek voor dit eetmoment: alle unieke bevestigde maaltijden met bordfoto,
     * nieuwste eerst. De AI krijgt van elk de beschrijving en opent alleen de foto's die lijken.
     * De Agent Runtime neemt hooguit 10 MB aan bijlagen per job aan, vandaar het budget in bytes.
     */
    private fun library(meal: Meal, ownBytes: Int): List<ReferenceExample> {
        var budget = MAX_JOB_BYTES - ownBytes
        return meals.findBetween(meal.eatenAt.minus(LIBRARY_WINDOW), meal.eatenAt)
            .filter { it.id != meal.id && it.isConfirmed() && it.referenceRole == ReferenceRole.REFERENTIE }
            .sortedByDescending { it.eatenAt }
            .take(MAX_LIBRARY)
            .mapNotNull { m ->
                val photo = photos.load(m.referencePhotoId) ?: return@mapNotNull null
                if (photo.bytes.size > budget) return@mapNotNull null
                budget -= photo.bytes.size
                val date = m.eatenAt.atZone(ZONE).toLocalDate()
                val items = m.items.joinToString("; ") {
                    "${it.name} ${it.grams} g" + if (it.basis == ItemBasis.GEWOGEN) " (gewogen)" else " (bevestigd)"
                }
                ReferenceExample(photo, "${m.title} van $date, ${m.referenceDescription}. Hoeveelheden: $items")
            }
    }

    private fun Meal.isConfirmed() = status == MealStatus.BEVESTIGD || status == MealStatus.VERBETERD

    private fun Meal.withoutReference() = copy(
        referenceRole = ReferenceRole.ONBEKEND,
        referenceDescription = "",
        referencePhotoId = "",
        duplicateOf = "",
    )

    /** De laatste bevestigde maaltijden van de afgelopen twee weken, als voorbeeld voor de AI. */
    private fun recentExamples(meal: Meal): List<Meal> =
        meals.findBetween(meal.eatenAt.minus(Duration.ofDays(14)), meal.eatenAt)
            .filter { it.id != meal.id && (it.status == MealStatus.BEVESTIGD || it.status == MealStatus.VERBETERD) }
            .takeLast(MAX_EXAMPLES)

    /** Bevestigde maaltijden van de afgelopen twee maanden met minstens één gewogen onderdeel. */
    private fun weighedMeals(meal: Meal): List<Meal> =
        meals.findBetween(meal.eatenAt.minus(REFERENCE_WINDOW), meal.eatenAt)
            .filter { it.id != meal.id && (it.status == MealStatus.BEVESTIGD || it.status == MealStatus.VERBETERD) }
            .filter { m -> m.items.any { it.basis == ItemBasis.GEWOGEN } }

    /**
     * De gewogen maaltijden met foto die qua tijdstip het dichtst bij dit eetmoment liggen: wie om
     * half acht eet, heeft het meest aan de gewogen ontbijten. Bij gelijke afstand wint de nieuwste.
     */
    private fun references(meal: Meal, weighed: List<Meal>): List<ReferenceExample> {
        fun minuteOfDay(m: Meal) = m.eatenAt.atZone(ZONE).toLocalTime().toSecondOfDay() / 60
        val target = minuteOfDay(meal)
        fun distance(m: Meal) = kotlin.math.abs(minuteOfDay(m) - target).let { minOf(it, 1440 - it) }
        return weighed
            .filter { it.photoIds.isNotEmpty() }
            .sortedWith(compareBy<Meal> { distance(it) }.thenByDescending { it.eatenAt })
            .take(MAX_REFERENCES)
            .mapNotNull { m ->
                val photo = photos.load(m.photoIds.first()) ?: return@mapNotNull null
                val date = m.eatenAt.atZone(ZONE).toLocalDate()
                val items = m.items.joinToString("; ") {
                    "${it.name} ${it.grams} g" + if (it.basis == ItemBasis.GEWOGEN) " (gewogen)" else " (geschat)"
                }
                ReferenceExample(photo, "${m.title} van $date: $items")
            }
    }

    private fun weighedPortions(weighed: List<Meal>): Map<String, List<Int>> =
        weighed.flatMap { it.items }.filter { it.basis == ItemBasis.GEWOGEN }
            .groupBy({ it.name.trim() }, { it.grams })
            .mapValues { it.value.takeLast(6) }

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
        catalogueExecutor.shutdownNow()
    }

    companion object {
        /** Zo begint de opmerking als het model de foto niet kon openen (zie de voedingsprompt). */
        const val PHOTO_NOT_READ = "FOTO NIET GELEZEN"

        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        private val STUCK_AFTER: Duration = Duration.ofMinutes(10)
        private const val MAX_EXAMPLES = 20
        private const val MAX_REFERENCES = 3
        private val REFERENCE_WINDOW: Duration = Duration.ofDays(60)
        private val LIBRARY_WINDOW: Duration = Duration.ofDays(180)
        private const val MAX_LIBRARY = 40
        private const val MAX_JOB_BYTES = 8 * 1024 * 1024
    }
}
