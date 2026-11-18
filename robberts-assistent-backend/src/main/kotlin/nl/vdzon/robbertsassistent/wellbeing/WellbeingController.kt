package nl.vdzon.robbertsassistent.wellbeing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Service
class CheckInService(private val repository: CheckInRepository) {

    fun record(
        hunger: Int?,
        energy: Int?,
        note: String = "",
        source: CheckInSource = CheckInSource.APP,
        mealId: String? = null,
        at: Instant? = null,
    ): CheckIn {
        require(hunger != null || energy != null) { "Geef honger en/of energie op" }
        require(hunger == null || hunger in 0..MAX_HUNGER) { "Honger loopt van 0 (geen) tot $MAX_HUNGER (slap van de honger)" }
        require(energy == null || energy in 1..5) { "Energie loopt van 1 (moe) tot 5 (fit)" }
        return repository.save(
            CheckIn(
                id = UUID.randomUUID().toString(),
                at = at ?: Instant.now(),
                hunger = hunger,
                energy = energy,
                note = note.trim(),
                source = source,
                mealId = mealId?.takeIf { it.isNotBlank() },
            ),
        )
    }

    fun day(date: LocalDate): List<CheckIn> =
        repository.findBetween(date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant())

    fun between(from: Instant, to: Instant): List<CheckIn> = repository.findBetween(from, to)

    fun delete(id: String) = repository.delete(id)

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

        /** Hoogste waarde van de hongerschaal: slap van de honger. */
        const val MAX_HUNGER = 4
    }
}

/**
 * Drie vaste vragen per dag. Die zijn nodig voor de latere analyse: wie alleen logt bij een
 * opvallend gevoel, heeft alleen uitschieters en geen gewone momenten om mee te vergelijken.
 * Een vraag wordt overgeslagen als er in het afgelopen uur al een check-in was.
 */
@Component
class CheckInPromptScheduler(
    private val checkIns: CheckInService,
    private val push: PushService,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 30 10,15,20 * * *", zone = "Europe/Amsterdam")
    fun prompt() {
        runCatching {
            val now = clock.instant()
            if (checkIns.between(now.minus(QUIET_AFTER_CHECKIN), now.plusSeconds(1)).isNotEmpty()) {
                logger.info("Check-in-vraag overgeslagen: er was het afgelopen uur al een check-in")
                return
            }
            push.sendToAll("Hoe voel je je?", "Twee tikken: honger en energie.", mapOf("type" to PUSH_TYPE))
        }.onFailure { logger.warn("Check-in-vraag versturen mislukt: {}", it.message) }
    }

    companion object {
        const val PUSH_TYPE = "checkin"
        private val QUIET_AFTER_CHECKIN: Duration = Duration.ofHours(1)
    }
}

data class CheckInRequest(
    val hunger: Int? = null,
    val energy: Int? = null,
    val note: String = "",
    val source: String? = null,
    val mealId: String? = null,
    val at: String? = null,
)

data class CheckInDto(
    val id: String,
    val at: String,
    val hunger: Int?,
    val energy: Int?,
    val note: String,
    val source: String,
    val mealId: String?,
)

data class CheckInDayDto(val date: String, val checkIns: List<CheckInDto>)

@RestController
class WellbeingController(
    private val authService: AuthService,
    private val checkIns: CheckInService,
) {
    @PostMapping("/api/v1/checkins")
    fun record(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: CheckInRequest,
    ): CheckInDto {
        authService.requireAuthorization(authorization)
        val at = request.at?.takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldig tijdstip") }
        }
        val source = runCatching { CheckInSource.valueOf(request.source.orEmpty().uppercase()) }
            .getOrDefault(CheckInSource.APP)
        return try {
            checkIns.record(request.hunger, request.energy, request.note, source, request.mealId, at).toDto()
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    /** Check-ins van één dag; zonder `date` vandaag (Europe/Amsterdam). */
    @GetMapping("/api/v1/checkins")
    fun day(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("date", required = false) date: String?,
    ): CheckInDayDto {
        authService.requireAuthorization(authorization)
        val day = runCatching { date?.let(LocalDate::parse) ?: LocalDate.now(CheckInService.ZONE) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige datum") }
        return CheckInDayDto(day.toString(), checkIns.day(day).map { it.toDto() })
    }

    @DeleteMapping("/api/v1/checkins/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        authService.requireAuthorization(authorization)
        checkIns.delete(id)
        return ResponseEntity.noContent().build()
    }
}

private fun CheckIn.toDto() = CheckInDto(id, at.toString(), hunger, energy, note, source.name, mealId)
