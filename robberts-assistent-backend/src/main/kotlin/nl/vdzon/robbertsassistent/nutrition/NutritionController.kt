package nl.vdzon.robbertsassistent.nutrition

import java.time.Instant
import java.time.LocalDate
import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
import org.springframework.web.server.ResponseStatusException

data class MealItemDto(
    val name: String = "",
    val grams: Int = 0,
    val kcal: Int = 0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val vegetableG: Int = 0,
    val fruitG: Int = 0,
    val uncertain: Boolean = false,
    val basis: String = ItemBasis.GESCHAT.name,
)

data class MealDto(
    val id: String,
    val eatenAt: String,
    val title: String,
    val photoIds: List<String>,
    val note: String,
    val status: String,
    val kcal: Int,
    val items: List<MealItemDto>,
    val remark: String,
    val learned: String,
    val error: String,
)

data class TotalsDto(
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val vegetableG: Int,
    val fruitG: Int,
)

data class NutritionDayDto(val date: String, val totals: TotalsDto, val meals: List<MealDto>)

data class ConfirmMealRequest(val items: List<MealItemDto>? = null, val title: String? = null, val eatenAt: String? = null)
data class CorrectMealRequest(val text: String = "")
data class FoodMemoryDto(val text: String = "")

/**
 * REST-API van het eetdagboek. Een eetmoment aanmaken geeft direct de maaltijd terug met status
 * ANALYSEREN; de app haalt het voorstel daarna op via `GET /api/v1/meals/{id}` (of na de push).
 */
@RestController
class NutritionController(
    private val authService: AuthService,
    private val mealService: MealService,
) {
    @PostMapping("/api/v1/meals", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("photos", required = false) photos: List<MultipartFile>?,
        @RequestParam("note", required = false, defaultValue = "") note: String,
        @RequestParam("eatenAt", required = false) eatenAt: String?,
    ): MealDto {
        authService.requireAuthorization(authorization)
        return mealService.create(uploads(photos), note, parseInstant(eatenAt)).toDto()
    }

    @PostMapping("/api/v1/meals/{id}/photos", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun addPhotos(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestParam("photos", required = false) photos: List<MultipartFile>?,
        @RequestParam("note", required = false, defaultValue = "") note: String,
    ): MealDto {
        authService.requireAuthorization(authorization)
        val uploads = uploads(photos)
        if (uploads.isEmpty() && note.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Stuur minstens één foto of een notitie")
        }
        return mealService.addPhotos(id, uploads, note).toDto()
    }

    @GetMapping("/api/v1/meals/{id}")
    fun get(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): MealDto {
        authService.requireAuthorization(authorization)
        return mealService.get(id).toDto()
    }

    /** Dagoverzicht; zonder `date` de dag van vandaag (Europe/Amsterdam). */
    @GetMapping("/api/v1/meals")
    fun day(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("date", required = false) date: String?,
    ): NutritionDayDto {
        authService.requireAuthorization(authorization)
        val day = runCatching { date?.let(LocalDate::parse) ?: LocalDate.now(MealService.ZONE) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige datum") }
        return mealService.day(day).toDto()
    }

    /** Bevestigen. Zonder body (of zonder `items`) het voorstel overnemen; met `items` de eigen versie. */
    @PutMapping("/api/v1/meals/{id}")
    fun confirm(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody(required = false) request: ConfirmMealRequest?,
    ): MealDto {
        authService.requireAuthorization(authorization)
        return mealService.confirm(
            id = id,
            items = request?.items?.map { it.toItem() },
            title = request?.title,
            eatenAt = parseInstant(request?.eatenAt),
        ).toDto()
    }

    @PostMapping("/api/v1/meals/{id}/correct")
    fun correct(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: CorrectMealRequest,
    ): MealDto {
        authService.requireAuthorization(authorization)
        return mealService.correctByText(id, request.text).toDto()
    }

    @PostMapping("/api/v1/meals/{id}/retry")
    fun retry(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): MealDto {
        authService.requireAuthorization(authorization)
        return mealService.retry(id).toDto()
    }

    @DeleteMapping("/api/v1/meals/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        authService.requireAuthorization(authorization)
        mealService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/v1/meals/photos/{photoId}")
    fun photo(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable photoId: String,
    ): ResponseEntity<ByteArray> {
        authService.requireAuthorization(authorization)
        val stored = mealService.photo(photoId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Foto niet gevonden")
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(stored.contentType)).body(stored.bytes)
    }

    @GetMapping("/api/v1/nutrition/memory")
    fun memory(@RequestHeader("Authorization", required = false) authorization: String?): FoodMemoryDto {
        authService.requireAuthorization(authorization)
        return FoodMemoryDto(mealService.memoryText())
    }

    @PutMapping("/api/v1/nutrition/memory")
    fun updateMemory(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: FoodMemoryDto,
    ): FoodMemoryDto {
        authService.requireAuthorization(authorization)
        return FoodMemoryDto(mealService.updateMemory(request.text))
    }

    @ExceptionHandler(MealNotFoundException::class)
    fun notFound(e: MealNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to (e.message ?: "Niet gevonden")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to (e.message ?: "Ongeldig verzoek")))

    @ExceptionHandler(IllegalStateException::class)
    fun conflict(e: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to (e.message ?: "Kan nu niet")))

    private fun uploads(photos: List<MultipartFile>?): List<MealPhotoUpload> =
        (photos ?: emptyList()).filter { !it.isEmpty }
            .map { MealPhotoUpload(it.bytes, it.contentType ?: MediaType.IMAGE_JPEG_VALUE) }

    private fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldig tijdstip") }
        }
}

private fun MealItemDto.toItem() = MealItem(
    name = name.trim().ifEmpty { "Onbekend" },
    grams = grams.coerceAtLeast(0),
    kcal = kcal.coerceAtLeast(0),
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
    vegetableG = vegetableG,
    fruitG = fruitG,
    uncertain = uncertain,
    basis = runCatching { ItemBasis.valueOf(basis) }.getOrDefault(ItemBasis.GESCHAT),
)

private fun MealItem.toDto() = MealItemDto(name, grams, kcal, proteinG, carbsG, fatG, fiberG, vegetableG, fruitG, uncertain, basis.name)

private fun Meal.toDto() = MealDto(
    id = id,
    eatenAt = eatenAt.toString(),
    title = title,
    photoIds = photoIds,
    note = note,
    status = status.name,
    kcal = kcal,
    items = items.map { it.toDto() },
    remark = remark,
    learned = learned,
    error = error,
)

private fun NutritionDay.toDto() = NutritionDayDto(
    date = date.toString(),
    totals = TotalsDto(totals.kcal, totals.proteinG, totals.carbsG, totals.fatG, totals.fiberG, totals.vegetableG, totals.fruitG),
    meals = meals.map { it.toDto() },
)
