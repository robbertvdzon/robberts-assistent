package nl.vdzon.robbertsassistent.reminders

import nl.vdzon.robbertsassistent.auth.AuthService
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

/**
 * REST-API voor reminders (de app toont/beheert ze). Auth-gated. Reminders leveren op tijd een
 * push-notificatie; eenmalig of herhalend (recurrence).
 */
@RestController
class RemindersController(
    private val authService: AuthService,
    private val remindersService: RemindersService,
) {
    @GetMapping("/api/v1/reminders")
    fun list(@RequestHeader("Authorization", required = false) authorization: String?): RemindersResponse {
        authService.requireAuthorization(authorization)
        return RemindersResponse(remindersService.list().map { it.toResponse() })
    }

    @PostMapping("/api/v1/reminders")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: CreateReminderRequest,
    ): ReminderResponse {
        authService.requireAuthorization(authorization)
        if (request.message.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "message mag niet leeg zijn")
        }
        val dueAt = try {
            Instant.parse(request.dueAt)
        } catch (ex: DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "dueAt moet een ISO-8601 tijdstip zijn")
        }
        return remindersService.create(request.message, dueAt, request.recurrence?.toRecurrence()).toResponse()
    }

    @DeleteMapping("/api/v1/reminders/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): RemindersResponse {
        authService.requireAuthorization(authorization)
        remindersService.delete(id)
        return RemindersResponse(remindersService.list().map { it.toResponse() })
    }
}
