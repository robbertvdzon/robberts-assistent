package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class CreateWatchRequest(
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: WatchFrequency,
    val notifyOnFound: Boolean = false,
)

data class WatchResponse(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: WatchFrequency,
    val notifyOnFound: Boolean,
    val status: WatchStatus,
    val statusDescription: String,
    val lastCheckedAt: Instant?,
    val active: Boolean,
)

data class WatchesResponse(val watches: List<WatchResponse>)
data class WatchErrorResponse(val message: String)

@RestController
class WatchesController(
    private val authService: AuthService,
    private val watchService: WatchService,
) {
    @GetMapping("/api/v1/watches")
    fun list(@RequestHeader("Authorization", required = false) authorization: String?): WatchesResponse {
        authService.requireAuthorization(authorization)
        return response()
    }

    @PostMapping("/api/v1/watches")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: CreateWatchRequest,
    ): WatchResponse {
        authService.requireAuthorization(authorization)
        return watchService.create(
            request.title,
            request.url,
            request.instruction,
            request.frequency,
            request.notifyOnFound,
        ).toResponse()
    }

    @DeleteMapping("/api/v1/watches/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): WatchesResponse {
        authService.requireAuthorization(authorization)
        watchService.delete(id)
        return response()
    }

    @ExceptionHandler(WatchValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalid(ex: WatchValidationException) = WatchErrorResponse(ex.message ?: "Ongeldige zoekopdracht.")

    private fun response() = WatchesResponse(watchService.list().map { it.toResponse() })
}

private fun Watch.toResponse() = WatchResponse(
    id = id,
    title = title,
    url = url,
    instruction = instruction,
    frequency = frequency,
    notifyOnFound = notifyOnFound,
    status = status,
    statusDescription = statusDescription,
    lastCheckedAt = lastCheckedAt,
    active = active,
)
