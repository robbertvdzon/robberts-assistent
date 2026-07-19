package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.auth.AuthService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

/**
 * REST-API voor de persistente assistent-chat. De app stuurt een multipart-request met een
 * tekstbericht, optioneel een `conversationId` en nul of meer foto's; de backend slaat de foto's
 * op, laat de assistent (met tools) antwoorden, en geeft het antwoord + de bijgewerkte conversatie
 * terug. Alles auth-gated. Analoog aan `gardenchat.GardenChatController`.
 */
@RestController
class AssistantController(
    private val authService: AuthService,
    private val assistantService: AssistantService,
    @Qualifier("assistantPhotoStorage") private val photoStorage: PhotoStorage,
) {
    @PostMapping("/api/v1/assistant/chat", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun chat(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("message", required = false, defaultValue = "") message: String,
        @RequestParam("conversationId", required = false) conversationId: String?,
        @RequestParam("photos", required = false) photos: List<MultipartFile>?,
    ): AssistantChatResponse {
        authService.requireAuthorization(authorization)
        val uploads = (photos ?: emptyList())
            .filter { !it.isEmpty }
            .map { PhotoUpload(it.bytes, it.contentType ?: MediaType.IMAGE_JPEG_VALUE) }
        if (message.isBlank() && uploads.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Stuur een bericht en/of minstens één foto")
        }
        val result = assistantService.chat(conversationId, message, uploads)
        return AssistantChatResponse(
            conversationId = result.conversationId,
            title = result.title,
            reply = result.reply,
            messages = result.messages.map { it.toDto() },
        )
    }

    @GetMapping("/api/v1/assistant/conversations")
    fun conversations(
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): List<ConversationSummaryDto> {
        authService.requireAuthorization(authorization)
        return assistantService.listConversations().map { it.toSummaryDto() }
    }

    @GetMapping("/api/v1/assistant/conversations/{id}")
    fun conversation(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): AssistantConversationResponse {
        authService.requireAuthorization(authorization)
        val conversation = assistantService.conversation(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Gesprek niet gevonden")
        return AssistantConversationResponse(
            conversationId = conversation.id,
            title = conversation.title?.takeIf { it.isNotBlank() } ?: "Nieuw gesprek",
            messages = conversation.messages.map { it.toDto() },
        )
    }

    @GetMapping("/api/v1/assistant/photos/{id}")
    fun photo(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): ResponseEntity<ByteArray> {
        authService.requireAuthorization(authorization)
        val stored = photoStorage.load(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Foto niet gevonden")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.contentType))
            .body(stored.bytes)
    }
}
