package nl.vdzon.robbertsassistent.push

import java.time.Instant
import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
class NotificationController(private val auth: AuthService, private val history: NotificationHistory) {
    @GetMapping("/api/v1/notifications")
    fun list(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam(required = false) before: String?,
        @RequestParam(defaultValue = "50") limit: Int,
    ): NotificationPage {
        auth.requireAuthorization(authorization)
        if (limit !in 1..100 || (before != null && !validId(before))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige paginering")
        }
        return history.page(before, limit)
    }

    @PostMapping("/api/v1/notifications/{id}/read")
    fun read(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): AppNotification {
        auth.requireAuthorization(authorization)
        if (!validId(id)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige melding")
        return history.markRead(id, Instant.now())
    }

    @ExceptionHandler(NotificationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun missing(): Map<String, String> = mapOf("message" to "Melding niet gevonden")

    private fun validId(id: String) = id.matches(Regex("[a-zA-Z0-9-]{1,100}"))
}
