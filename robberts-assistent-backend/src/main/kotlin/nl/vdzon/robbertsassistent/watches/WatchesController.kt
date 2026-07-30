package nl.vdzon.robbertsassistent.watches

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

/**
 * REST-API voor watches: langdurige zoekopdrachten die periodiek een webpagina controleren.
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
        @RequestBody request: CreateWatchRequest,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        if (request.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title mag niet leeg zijn")
        }
        if (request.url.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "url mag niet leeg zijn")
        }
        if (request.instruction.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "instruction mag niet leeg zijn")
        }
        val frequency = runCatching { WatchFrequency.valueOf(request.frequency) }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "frequency moet KANTOORUREN of DAGELIJKS zijn")
        }
        return watchesService.create(request.title, request.url, request.instruction, frequency).toResponse()
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
}
