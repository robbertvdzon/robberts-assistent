package nl.vdzon.robbertsassistent.gardenchat

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import java.time.Instant

/**
 * Eén bericht in een moestuin-chat. [role] is "user" of "assistant". [imageIds] verwijst naar de
 * foto's die bij dit (gebruikers)bericht horen — opgeslagen via [PhotoStorage].
 */
data class GardenMessage(
    val id: String,
    val role: String,
    val text: String,
    val imageIds: List<String> = emptyList(),
    val createdAt: Instant,
) {
    /** Voor de AI-historie: alleen tekst (de foto's worden per beurt als Media meegestuurd). */
    fun toSpringMessage(): Message =
        if (role == ROLE_ASSISTANT) AssistantMessage(text) else UserMessage(text)

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/** Een moestuin-chat: een reeks berichten, oplopend in tijd. */
data class Conversation(
    val id: String,
    val messages: List<GardenMessage> = emptyList(),
    val createdAt: Instant,
)
