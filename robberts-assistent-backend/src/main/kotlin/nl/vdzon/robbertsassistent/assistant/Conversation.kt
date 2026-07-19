package nl.vdzon.robbertsassistent.assistant

import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import java.time.Instant

/**
 * Eén bericht in een assistent-gesprek. [role] is "user" of "assistant". [imageIds] verwijst naar
 * de foto's die bij dit (gebruikers)bericht horen — opgeslagen via [PhotoStorage].
 */
data class ConversationMessage(
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

/** Een assistent-gesprek: een reeks berichten, oplopend in tijd, met een (zelf-verzonnen) titel. */
data class Conversation(
    val id: String,
    val title: String? = null,
    val messages: List<ConversationMessage> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)
