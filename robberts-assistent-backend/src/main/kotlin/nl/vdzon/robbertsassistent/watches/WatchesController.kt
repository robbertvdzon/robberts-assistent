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
 * REST-API voor zoekopdrachten (de "Zoekopdrachten"-tab beheert ze). Auth-gated, zelfde stijl als
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
        @RequestBody request: CreateWatchRequest,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        val frequency = parseFrequency(request.frequency)
        validate(request.title, request.url, request.instruction)
        return watchesService.create(
            title = request.title.trim(),
            url = request.url.trim(),
            instruction = request.instruction.trim(),
            frequency = frequency,
            notifyOnFound = request.notifyOnFound,
        ).toResponse()
    }

    @PutMapping("/api/v1/watches/{id}")
    fun update(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: UpdateWatchRequest,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        val frequency = parseFrequency(request.frequency)
        validate(request.title, request.url, request.instruction)
        return watchesService.update(
            id = id,
            title = request.title.trim(),
            url = request.url.trim(),
            instruction = request.instruction.trim(),
            frequency = frequency,
            notifyOnFound = request.notifyOnFound,
        ).toResponse()
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

    private fun validate(title: String, url: String, instruction: String) {
        if (title.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title mag niet leeg zijn")
        if (url.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "url mag niet leeg zijn")
        if (instruction.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "instruction mag niet leeg zijn")
        }
    }

    private fun parseFrequency(value: String): WatchFrequency = try {
        WatchFrequency.valueOf(value)
    } catch (ex: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "frequency moet KANTOORUREN of DAGELIJKS zijn")
    }
}
