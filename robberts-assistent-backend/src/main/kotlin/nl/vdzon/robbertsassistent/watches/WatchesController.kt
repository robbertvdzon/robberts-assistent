package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * REST-API voor de zoekopdrachten ("houd deze pagina in de gaten"). Auth-gated, zelfde stijl als
 * `reminders.RemindersController`.
 */
@RestController
class WatchesController(
    private val authService: AuthService,
    private val watchesService: WatchesService,
) {
    @GetMapping("/api/v1/watches")
    fun list(@RequestHeader("Authorization", required = false) authorization: String?): WatchesResponse {
        authService.requireAuthorization(authorization)
        return WatchesResponse(watchesService.list().map { it.toResponse() })
    }

    @PostMapping("/api/v1/watches")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: SaveWatchRequest,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        val validated = request.validated()
        return watchesService.create(
            title = validated.title,
            url = validated.url,
            instruction = validated.instruction,
            frequency = validated.frequency,
            pushOnFound = request.pushOnFound,
        ).toResponse()
    }

    @PutMapping("/api/v1/watches/{id}")
    fun update(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: SaveWatchRequest,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        val validated = request.validated()
        return watchesService.update(
            id = id,
            title = validated.title,
            url = validated.url,
            instruction = validated.instruction,
            frequency = validated.frequency,
            pushOnFound = request.pushOnFound,
            active = request.active,
        )?.toResponse() ?: throw notFound(id)
    }

    @DeleteMapping("/api/v1/watches/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): WatchesResponse {
        authService.requireAuthorization(authorization)
        watchesService.delete(id)
        return WatchesResponse(watchesService.list().map { it.toResponse() })
    }

    /** Direct nu controleren; geeft de bijgewerkte zoekopdracht terug. */
    @PostMapping("/api/v1/watches/{id}/check")
    fun check(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        val watch = watchesService.find(id) ?: throw notFound(id)
        return watchesService.check(watch).toResponse()
    }

    private fun notFound(id: String) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "Zoekopdracht $id bestaat niet")

    /** Gevalideerde, opgeschoonde invoer van [SaveWatchRequest]. */
    private class ValidRequest(
        val title: String,
        val url: String,
        val instruction: String,
        val frequency: WatchFrequency,
    )

    private fun SaveWatchRequest.validated(): ValidRequest {
        val title = title.trim()
        val url = url.trim()
        if (title.isBlank()) badRequest("title mag niet leeg zijn")
        if (url.isBlank()) badRequest("url mag niet leeg zijn")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            badRequest("url moet met http:// of https:// beginnen")
        }
        val frequency = WatchFrequency.fromName(frequency)
            ?: badRequest("frequency moet KANTOORUREN of DAGELIJKS zijn")
        return ValidRequest(title, url, instruction.trim(), frequency)
    }

    private fun badRequest(message: String): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
