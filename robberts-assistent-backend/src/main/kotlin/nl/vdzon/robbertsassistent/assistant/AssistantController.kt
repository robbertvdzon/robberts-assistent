package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class AssistantController(
    private val authService: AuthService,
    private val assistantService: AssistantService,
) {
    @PostMapping("/api/v1/assistant/message")
    fun message(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: AssistantMessageRequest,
    ): AssistantMessageResponse {
        authService.requireAuthorization(authorization)
        return AssistantMessageResponse(text = assistantService.reply(request.text))
    }
}
