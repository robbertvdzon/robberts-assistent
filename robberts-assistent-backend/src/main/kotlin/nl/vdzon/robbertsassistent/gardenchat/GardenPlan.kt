package nl.vdzon.robbertsassistent.gardenchat

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Eén vastlegging bij een moestuinbak: wat er op [date] in stond, met foto's. Samen vormen de
 * vastleggingen de geschiedenis van de bak.
 */
data class BedEntry(
    val id: String,
    val date: LocalDate,
    val text: String,
    val photoIds: List<String>,
    val createdAt: Instant,
)

/**
 * Een moestuinbak op de plattegrond. Plaats en grootte zijn fracties (0..1) van de breedte en
 * hoogte van de plattegrond, zodat de tekening op elk scherm klopt.
 */
data class GardenBed(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val entries: List<BedEntry> = emptyList(),
)

/** De plattegrond zelf: een optionele achtergrond (foto of tekening van de tuin) en de verhouding. */
data class GardenPlan(val backgroundPhotoId: String = "", val aspectRatio: Double = 1.0)

class BedNotFoundException(id: String) : RuntimeException("Bak $id niet gevonden")

interface GardenPlanRepository {
    fun plan(): GardenPlan
    fun savePlan(plan: GardenPlan)
    fun beds(): List<GardenBed>
    fun findBed(id: String): GardenBed?
    fun saveBed(bed: GardenBed): GardenBed
    fun deleteBed(id: String)
}

class InMemoryGardenPlanRepository : GardenPlanRepository {
    @Volatile
    private var plan = GardenPlan()
    private val beds = ConcurrentHashMap<String, GardenBed>()

    override fun plan() = plan
    override fun savePlan(plan: GardenPlan) {
        this.plan = plan
    }

    override fun beds() = beds.values.toList()
    override fun findBed(id: String) = beds[id]
    override fun saveBed(bed: GardenBed) = bed.also { beds[it.id] = it }
    override fun deleteBed(id: String) {
        beds.remove(id)
    }
}

/** Eén document per bak in `garden-beds` (vastleggingen als array) en `garden-plan/plan`. */
class FirestoreGardenPlanRepository(private val firestore: Firestore) : GardenPlanRepository {
    private val bedCollection get() = firestore.collection("garden-beds")
    private val planDocument get() = firestore.collection("garden-plan").document("plan")

    override fun plan(): GardenPlan {
        val snapshot = planDocument.get().get()
        if (!snapshot.exists()) return GardenPlan()
        return GardenPlan(snapshot.getString("backgroundPhotoId").orEmpty(), snapshot.getDouble("aspectRatio") ?: 1.0)
    }

    override fun savePlan(plan: GardenPlan) {
        planDocument.set(mapOf("backgroundPhotoId" to plan.backgroundPhotoId, "aspectRatio" to plan.aspectRatio)).get()
    }

    override fun beds(): List<GardenBed> = bedCollection.get().get().documents.map { it.toBed() }

    override fun findBed(id: String): GardenBed? =
        bedCollection.document(id).get().get().takeIf { it.exists() }?.toBed()

    override fun saveBed(bed: GardenBed): GardenBed {
        bedCollection.document(bed.id).set(
            mapOf(
                "name" to bed.name,
                "x" to bed.x,
                "y" to bed.y,
                "width" to bed.width,
                "height" to bed.height,
                "entries" to bed.entries.map {
                    mapOf(
                        "id" to it.id,
                        "date" to it.date.toString(),
                        "text" to it.text,
                        "photoIds" to it.photoIds,
                        "createdAt" to it.createdAt.toEpochMilli(),
                    )
                },
            ),
        ).get()
        return bed
    }

    override fun deleteBed(id: String) {
        bedCollection.document(id).delete().get()
    }

    private fun DocumentSnapshot.toBed(): GardenBed {
        @Suppress("UNCHECKED_CAST")
        val raw = get("entries") as? List<Map<String, Any?>> ?: emptyList()
        return GardenBed(
            id = id,
            name = getString("name").orEmpty(),
            x = getDouble("x") ?: 0.0,
            y = getDouble("y") ?: 0.0,
            width = getDouble("width") ?: 0.2,
            height = getDouble("height") ?: 0.2,
            entries = raw.map { e ->
                BedEntry(
                    id = e["id"] as? String ?: "",
                    date = runCatching { LocalDate.parse(e["date"] as String) }.getOrDefault(LocalDate.EPOCH),
                    text = e["text"] as? String ?: "",
                    photoIds = (e["photoIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    createdAt = Instant.ofEpochMilli((e["createdAt"] as? Number)?.toLong() ?: 0L),
                )
            },
        )
    }
}

@Configuration
class GardenPlanStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun gardenPlanRepository(firebase: FirebaseProvider): GardenPlanRepository {
        if (!firebase.isConfigured) {
            logger.info("Moestuin-plattegrond: in-memory (geen Firebase-config)")
            return InMemoryGardenPlanRepository()
        }
        return runCatching { FirestoreGardenPlanRepository(firebase.firestore()) }
            .onSuccess { logger.info("Moestuin-plattegrond: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (plattegrond) faalde, val terug op in-memory", it)
                InMemoryGardenPlanRepository()
            }
    }
}

/**
 * De plattegrond van de moestuin met zijn bakken. Per bak een geschiedenis van vastleggingen
 * (datum, wat erin staat, foto's), zodat achteraf te zien is wat er in elke bak heeft gestaan en
 * de moestuinchat er vragen over kan beantwoorden.
 */
@Service
class GardenPlanService(private val repository: GardenPlanRepository, private val photos: PhotoStorage) {

    fun plan(): GardenPlan = repository.plan()

    fun beds(): List<GardenBed> = repository.beds().sortedBy { it.name.lowercase() }

    fun bed(id: String): GardenBed = repository.findBed(id) ?: throw BedNotFoundException(id)

    fun setBackground(upload: PhotoUpload, aspectRatio: Double): GardenPlan {
        require(aspectRatio in MIN_ASPECT..MAX_ASPECT) { "Ongeldige verhouding van de plattegrond" }
        val plan = GardenPlan(photos.store(upload.bytes, upload.contentType), aspectRatio)
        repository.savePlan(plan)
        return plan
    }

    fun createBed(name: String, x: Double, y: Double, width: Double, height: Double): GardenBed =
        repository.saveBed(placed(GardenBed(UUID.randomUUID().toString(), cleanName(name), x, y, width, height)))

    fun updateBed(id: String, name: String, x: Double, y: Double, width: Double, height: Double): GardenBed =
        repository.saveBed(placed(bed(id).copy(name = cleanName(name), x = x, y = y, width = width, height = height)))

    fun deleteBed(id: String) = repository.deleteBed(id)

    fun addEntry(bedId: String, text: String, uploads: List<PhotoUpload>, date: LocalDate?): GardenBed {
        require(text.isNotBlank() || uploads.isNotEmpty()) { "Voeg een foto of een tekst toe" }
        val bed = bed(bedId)
        val entry = BedEntry(
            id = UUID.randomUUID().toString(),
            date = date ?: LocalDate.now(ZONE),
            text = text.trim(),
            photoIds = uploads.map { photos.store(it.bytes, it.contentType) },
            createdAt = Instant.now(),
        )
        return repository.saveBed(bed.copy(entries = bed.entries + entry))
    }

    fun deleteEntry(bedId: String, entryId: String): GardenBed {
        val bed = bed(bedId)
        return repository.saveBed(bed.copy(entries = bed.entries.filterNot { it.id == entryId }))
    }

    /**
     * Wat de moestuinchat over de bakken moet weten. Met [bedId] de volledige geschiedenis van die
     * bak; zonder een regel per bak met de laatste vastlegging.
     */
    fun chatContext(bedId: String?): String {
        if (bedId != null) {
            val bed = repository.findBed(bedId) ?: return ""
            val history = bed.entries.sortedByDescending { it.date }.take(MAX_CONTEXT_ENTRIES)
                .joinToString("\n") { "- ${it.date}: ${it.text.ifBlank { "(alleen foto's)" }}" }
            return "De vraag gaat over de moestuinbak \"${bed.name}\". Vastgelegd over deze bak, nieuwste eerst:\n" +
                history.ifBlank { "(nog niets vastgelegd)" } +
                if (bed.latestPhotoIds().isEmpty()) "" else "\nDe meegestuurde foto's zonder eigen toelichting zijn de laatste foto's van deze bak."
        }
        val lines = beds().mapNotNull { bed ->
            val latest = bed.entries.maxByOrNull { it.date } ?: return@mapNotNull null
            "- ${bed.name} (${latest.date}): ${latest.text.ifBlank { "(alleen foto's)" }}"
        }
        return if (lines.isEmpty()) "" else "De moestuinbakken en wat er het laatst over is vastgelegd:\n" + lines.joinToString("\n")
    }

    /** De foto's van de laatste vastlegging met foto's, om met een vraag over de bak mee te sturen. */
    fun latestPhotos(bedId: String): List<StoredPhoto> =
        repository.findBed(bedId)?.latestPhotoIds().orEmpty().take(MAX_CONTEXT_PHOTOS).mapNotNull { photos.load(it) }

    private fun GardenBed.latestPhotoIds(): List<String> =
        entries.filter { it.photoIds.isNotEmpty() }.maxByOrNull { it.date }?.photoIds.orEmpty()

    private fun cleanName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Geef de bak een naam" } }.take(MAX_NAME)

    /** Houdt de bak binnen de plattegrond en niet kleiner dan aan te tikken is. */
    private fun placed(bed: GardenBed): GardenBed {
        val width = bed.width.coerceIn(MIN_SIZE, 1.0)
        val height = bed.height.coerceIn(MIN_SIZE, 1.0)
        return bed.copy(
            width = width,
            height = height,
            x = bed.x.coerceIn(0.0, 1.0 - width),
            y = bed.y.coerceIn(0.0, 1.0 - height),
        )
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        const val MIN_SIZE = 0.05
        const val MIN_ASPECT = 0.2
        const val MAX_ASPECT = 5.0
        const val MAX_NAME = 40
        const val MAX_CONTEXT_ENTRIES = 30
        const val MAX_CONTEXT_PHOTOS = 3
    }
}

data class BedEntryDto(val id: String, val date: String, val text: String, val photoIds: List<String>)

data class GardenBedDto(
    val id: String,
    val name: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val entries: List<BedEntryDto>,
)

data class GardenPlanDto(val backgroundPhotoId: String, val aspectRatio: Double, val beds: List<GardenBedDto>)

data class SaveBedRequest(
    val name: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.2,
    val height: Double = 0.2,
)

@RestController
class GardenPlanController(private val authService: AuthService, private val service: GardenPlanService) {

    @GetMapping("/api/v1/garden/plan")
    fun plan(@RequestHeader("Authorization", required = false) authorization: String?): GardenPlanDto {
        authService.requireAuthorization(authorization)
        return overview()
    }

    @PostMapping("/api/v1/garden/plan/background", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun background(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("photo") photo: MultipartFile,
        @RequestParam("aspectRatio") aspectRatio: Double,
    ): GardenPlanDto {
        authService.requireAuthorization(authorization)
        service.setBackground(PhotoUpload(photo.bytes, photo.contentType ?: MediaType.IMAGE_JPEG_VALUE), aspectRatio)
        return overview()
    }

    @PostMapping("/api/v1/garden/beds")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: SaveBedRequest,
    ): GardenBedDto {
        authService.requireAuthorization(authorization)
        return service.createBed(request.name, request.x, request.y, request.width, request.height).toDto()
    }

    @GetMapping("/api/v1/garden/beds/{id}")
    fun bed(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): GardenBedDto {
        authService.requireAuthorization(authorization)
        return service.bed(id).toDto()
    }

    @PutMapping("/api/v1/garden/beds/{id}")
    fun update(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: SaveBedRequest,
    ): GardenBedDto {
        authService.requireAuthorization(authorization)
        return service.updateBed(id, request.name, request.x, request.y, request.width, request.height).toDto()
    }

    @DeleteMapping("/api/v1/garden/beds/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        authService.requireAuthorization(authorization)
        service.deleteBed(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/api/v1/garden/beds/{id}/entries", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addEntry(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestParam("text", required = false, defaultValue = "") text: String,
        @RequestParam("date", required = false) date: String?,
        @RequestParam("photos", required = false) photos: List<MultipartFile>?,
    ): GardenBedDto {
        authService.requireAuthorization(authorization)
        val uploads = (photos ?: emptyList()).filter { !it.isEmpty }
            .map { PhotoUpload(it.bytes, it.contentType ?: MediaType.IMAGE_JPEG_VALUE) }
        val day = date?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrElse { throw IllegalArgumentException("Ongeldige datum") }
        }
        return service.addEntry(id, text, uploads, day).toDto()
    }

    @DeleteMapping("/api/v1/garden/beds/{id}/entries/{entryId}")
    fun deleteEntry(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @PathVariable entryId: String,
    ): GardenBedDto {
        authService.requireAuthorization(authorization)
        return service.deleteEntry(id, entryId).toDto()
    }

    @ExceptionHandler(BedNotFoundException::class)
    fun notFound(e: BedNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to (e.message ?: "Niet gevonden")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to (e.message ?: "Ongeldig verzoek")))

    private fun overview(): GardenPlanDto {
        val plan = service.plan()
        return GardenPlanDto(plan.backgroundPhotoId, plan.aspectRatio, service.beds().map { it.toDto() })
    }

    private fun GardenBed.toDto() = GardenBedDto(
        id, name, x, y, width, height,
        entries.sortedWith(compareByDescending<BedEntry> { it.date }.thenByDescending { it.createdAt })
            .map { BedEntryDto(it.id, it.date.toString(), it.text, it.photoIds) },
    )
}
