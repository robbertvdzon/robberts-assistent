package nl.vdzon.robbertsassistent.assistant

import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service

/**
 * Verwerkt een bericht van de gebruiker (spraak of getypt) via de chat-assistent ([ChatClient],
 * zie `assistant.ai.AiConfig`) en geeft het antwoord terug. De client heeft tools voor notities
 * (lezen/bijwerken) en windmetingen (waterinfo.rws.nl/windfinder.com) en roept die zelf aan wanneer
 * de vraag daarom vraagt.
 */
@Service
class AssistantService(private val chatClient: ChatClient) {
    fun reply(message: String): String =
        chatClient.prompt().user(message).call().content()
            ?: "Sorry, ik kon geen antwoord bedenken."
}
