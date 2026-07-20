package nl.vdzon.robbertsassistent.nightlychecks

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * REST-API voor het "Nachtchecks"-scherm: alle checks + hun laatste resultaat, historie per check,
 * en een check handmatig opnieuw draaien. Auth-gated.
 */
@RestController
class NightlyChecksController(
    private val authService: AuthService,
    private val service: NightlyChecksService,
) {
    @GetMapping("/api/v1/nightly-checks")
    fun list(@RequestHeader("Authorization", required = false) authorization: String?): NightlyChecksResponse {
        authService.requireAuthorization(authorization)
        return NightlyChecksResponse(service.list().map { it.toResponse() })
    }

    @GetMapping("/api/v1/nightly-checks/{id}/history")
    fun history(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestParam("limit", required = false) limit: Int?,
    ): CheckRunHistoryResponse {
        authService.requireAuthorization(authorization)
        val runs = service.history(id, limit ?: DEFAULT_HISTORY_LIMIT)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende check: $id")
        return CheckRunHistoryResponse(runs.map { it.toResponse() })
    }

    @PostMapping("/api/v1/nightly-checks/{id}/run")
    fun run(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): CheckRunResponse {
        authService.requireAuthorization(authorization)
        val run = service.runNow(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende check: $id")
        return run.toResponse()
    }

    private companion object {
        const val DEFAULT_HISTORY_LIMIT = 30
    }
}
