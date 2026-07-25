package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.reminders.ReminderResponse
import nl.vdzon.robbertsassistent.reminders.RemindersService
import nl.vdzon.robbertsassistent.reminders.toResponse
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

@RestController
class BriefingController(
    private val authService: AuthService,
    private val briefingService: BriefingService,
    private val remindersService: RemindersService,
    private val weatherMapStorage: WeatherMapStorage,
) {
    @GetMapping("/api/v1/briefing")
    fun briefing(@RequestHeader("Authorization", required = false) authorization: String?): BriefingResponse {
        authService.requireAuthorization(authorization)
        return briefingService.currentUpcoming()
    }

    /**
     * Bouwt de Upcoming-briefing (alle secties behalve systeemstatus) live opnieuw op, overschrijft
     * alléén de Upcoming-cache (incl. de weerkaart-PNG's van [WeatherMapSectionProvider]) en geeft
     * het verse resultaat terug — laat de Health check-cache ongewijzigd. Zelfde auth als de gewone
     * `GET /api/v1/briefing`.
     */
    @PostMapping("/api/v1/briefing/refresh")
    fun refresh(@RequestHeader("Authorization", required = false) authorization: String?): BriefingResponse {
        authService.requireAuthorization(authorization)
        return briefingService.refreshUpcoming()
    }

    /** De systeemstatus-sectie voor het 'Health check'-scherm, met een eigen `updatedAt` (zie [BriefingService]). */
    @GetMapping("/api/v1/briefing/health")
    fun health(@RequestHeader("Authorization", required = false) authorization: String?): BriefingResponse {
        authService.requireAuthorization(authorization)
        return briefingService.currentHealth()
    }

    /**
     * Bouwt uitsluitend de systeemstatus-sectie live opnieuw op, overschrijft alléén de Health
     * check-cache en geeft het verse resultaat terug — laat de Upcoming-cache ongewijzigd.
     */
    @PostMapping("/api/v1/briefing/health/refresh")
    fun refreshHealth(@RequestHeader("Authorization", required = false) authorization: String?): BriefingResponse {
        authService.requireAuthorization(authorization)
        return briefingService.refreshHealth()
    }

    /** Weerkaart-PNG onder sleutel `slot` (`morgen`), zie [WeatherMapSectionProvider]. */
    @GetMapping("/api/v1/briefing/weather-map/{slot}")
    fun weatherMap(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable slot: String,
    ): ResponseEntity<ByteArray> {
        authService.requireAuthorization(authorization)
        val bytes = weatherMapStorage.load(slot)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Weerkaart niet gevonden")
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noCache())
            .contentType(MediaType.IMAGE_PNG)
            .body(bytes)
    }

    /**
     * Eén-tap-actie vanuit de agenda-sectie: maakt een reminder ~1 uur vóór [CreateAgendaReminderRequest.startAt]
     * aan via de bestaande reminders-module (geen nieuwe reminder-logica hier).
     */
    @PostMapping("/api/v1/briefing/agenda-reminder")
    fun createAgendaReminder(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: CreateAgendaReminderRequest,
    ): ReminderResponse {
        authService.requireAuthorization(authorization)
        if (request.summary.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "summary mag niet leeg zijn")
        }
        val startAt = try {
            Instant.parse(request.startAt)
        } catch (ex: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "startAt moet een ISO-8601 tijdstip zijn")
        }
        val dueAt = startAt.minus(Duration.ofHours(1))
        return remindersService.create("${request.summary} (over 1 uur)", dueAt).toResponse()
    }
}

data class CreateAgendaReminderRequest(
    val summary: String = "",
    // ISO-8601 tijdstip van de afspraak zelf; de reminder wordt 1 uur ervoor gezet.
    val startAt: String = "",
)
