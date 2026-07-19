package nl.vdzon.robbertsassistent.couplings

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints voor het "Koppelingen"-scherm: de status van alle externe koppelingen ophalen en ze
 * live testen. Beide vereisen een geldig sessie-token.
 */
@RestController
class CouplingsController(
    private val authService: AuthService,
    private val service: CouplingsService,
) {
    /** Status van alle koppelingen (geconfigureerd + echt/fallback), zonder live-test. */
    @GetMapping("/api/v1/couplings")
    fun list(@RequestHeader("Authorization", required = false) authorization: String?): Map<String, Any> {
        authService.requireAuthorization(authorization)
        return mapOf("couplings" to service.statuses())
    }

    /** Test alle koppelingen live (parallel) en geef de status inclusief testresultaat terug. */
    @PostMapping("/api/v1/couplings/test")
    fun test(@RequestHeader("Authorization", required = false) authorization: String?): Map<String, Any> {
        authService.requireAuthorization(authorization)
        return mapOf("couplings" to service.testAll())
    }
}
