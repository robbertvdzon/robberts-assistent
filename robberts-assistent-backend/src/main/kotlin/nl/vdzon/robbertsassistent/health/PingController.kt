package nl.vdzon.robbertsassistent.health

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * Geauthenticeerde test-endpoint: bewijst de volledige keten (Google-login -> sessie-token ->
 * backend). In tegenstelling tot /healthz (open) vereist dit een geldig Bearer-token. Gebruikt o.a.
 * door de Groentetuin-app: inloggen -> testknop -> deze call moet {"status":"ok"} teruggeven.
 */
@RestController
class PingController(private val authService: AuthService) {
    @GetMapping("/api/v1/ping")
    fun ping(@RequestHeader("Authorization", required = false) authorization: String?): Map<String, String> {
        val identity = authService.requireAuthorization(authorization)
        return mapOf("status" to "ok", "user" to identity)
    }
}
