package nl.vdzon.robbertsassistent.alarms

import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.scheduling.RecurrenceDto
import nl.vdzon.robbertsassistent.scheduling.toDto
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.format.DateTimeParseException

data class AlarmResponse(
    val id: String,
    val message: String,
    val time: String,
    val recurrence: RecurrenceDto?,
    val active: Boolean,
)

data class AlarmsResponse(val alarms: List<AlarmResponse>)

data class CreateAlarmRequest(
    val message: String = "",
    val time: String = "",
    val recurrence: RecurrenceDto? = null,
)

fun Alarm.toResponse() = AlarmResponse(
    id = id,
    message = message,
    time = time.toString(),
    recurrence = recurrence?.toDto(),
    active = active,
)

/**
 * REST-API voor alarms. De app synct deze lijst en plant ze lokaal (AlarmManager). Auth-gated.
 */
@RestController
class AlarmsController(
    private val authService: AuthService,
    private val alarmsService: AlarmsService,
) {
    @GetMapping("/api/v1/alarms")
    fun list(@RequestHeader("Authorization", required = false) authorization: String?): AlarmsResponse {
        authService.requireAuthorization(authorization)
        return AlarmsResponse(alarmsService.list().map { it.toResponse() })
    }

    @PostMapping("/api/v1/alarms")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: CreateAlarmRequest,
    ): AlarmResponse {
        authService.requireAuthorization(authorization)
        if (request.message.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "message mag niet leeg zijn")
        }
        val time = try {
            Instant.parse(request.time)
        } catch (ex: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "time moet een ISO-8601 tijdstip zijn")
        }
        return alarmsService.create(request.message, time, request.recurrence?.toRecurrence()).toResponse()
    }

    @DeleteMapping("/api/v1/alarms/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): AlarmsResponse {
        authService.requireAuthorization(authorization)
        alarmsService.delete(id)
        return AlarmsResponse(alarmsService.list().map { it.toResponse() })
    }
}
