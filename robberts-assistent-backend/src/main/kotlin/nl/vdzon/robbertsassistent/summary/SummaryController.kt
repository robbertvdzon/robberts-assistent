package nl.vdzon.robbertsassistent.summary

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class SummaryController(
    private val authService: AuthService,
    private val summaryService: SummaryService,
) {
    @GetMapping("/api/v1/summary")
    fun summary(@RequestHeader("Authorization", required = false) authorization: String?): SummaryResponse {
        authService.requireAuthorization(authorization)
        return summaryService.current()
    }
}
