package nl.vdzon.robbertsassistent.push

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class FcmTokenRequest(val token: String = "")

/**
 * Endpoint waar de app zijn FCM-device-token registreert, zodat de backend er push naartoe kan
 * sturen. Auth-gated.
 */
@RestController
class PushController(
    private val authService: AuthService,
    private val fcmTokenStore: FcmTokenStore,
) {
    @PostMapping("/api/v1/fcm/token")
    fun register(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: FcmTokenRequest,
    ) {
        authService.requireAuthorization(authorization)
        if (request.token.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "token mag niet leeg zijn")
        }
        fcmTokenStore.add(request.token)
    }
}
