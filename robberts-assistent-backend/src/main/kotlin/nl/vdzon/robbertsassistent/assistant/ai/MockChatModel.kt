package nl.vdzon.robbertsassistent.assistant.ai

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux

/**
 * Deterministische, netwerk-vrije [ChatModel]-fake voor preview-omgevingen en unit-/
 * integratietests (zie [nl.vdzon.robbertsassistent.config.AppSecrets.effectiveMockAi]) — geen
 * OpenAI-kosten, geen flakiness door een externe API, en voorspelbaar te testen.
 *
 * Roept bewust GEEN tools aan: welke tool relevant is, is nu juist het deel dat een écht model
 * beslist. Toolgedrag test je daarom direct op [NotesTools]/[WindTools] zelf, niet via een
 * end-to-end chat-round-trip door dit mock-model.
 */
class MockChatModel : ChatModel {
    override fun call(prompt: Prompt): ChatResponse {
        val lastUserMessage = prompt.instructions
            .filterIsInstance<UserMessage>()
            .lastOrNull()
            ?.text
            .orEmpty()
        val reply = "Mock-antwoord (geen echte AI in deze omgeving) op: \"$lastUserMessage\""
        return ChatResponse(listOf(Generation(AssistantMessage(reply))))
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
}
