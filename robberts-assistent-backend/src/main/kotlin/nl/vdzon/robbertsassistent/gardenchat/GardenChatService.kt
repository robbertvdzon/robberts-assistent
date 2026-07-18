package nl.vdzon.robbertsassistent.gardenchat

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.content.Media
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.util.MimeTypeUtils
import java.time.Instant
import java.util.UUID

/** Een geüploade foto zoals binnengekomen in de controller. */
class PhotoUpload(val bytes: ByteArray, val contentType: String)

/** Resultaat van één chat-beurt. */
data class ChatResult(val conversationId: String, val reply: String, val messages: List<GardenMessage>)

/**
 * Kern van de moestuin-chat: bewaart de foto's, stuurt tekst + foto's + gesprekshistorie naar de
 * vision-AI, en bewaart zowel het vraag- als het antwoordbericht in de conversatie.
 */
@Service
class GardenChatService(
    @Qualifier("gardenChatClient") private val gardenChatClient: ChatClient,
    private val conversations: ConversationRepository,
    private val photos: PhotoStorage,
) {
    fun conversation(id: String): Conversation? = conversations.findById(id)

    fun chat(conversationId: String?, text: String, uploads: List<PhotoUpload>): ChatResult {
        val conversation = conversationId?.let { conversations.findById(it) } ?: conversations.create()

        // Foto's opslaan (voor weergave/historie) én als Media klaarzetten voor de AI (deze beurt).
        val imageIds = uploads.map { photos.store(it.bytes, it.contentType) }
        val media = uploads.map { Media(MimeTypeUtils.parseMimeType(it.contentType), ByteArrayResource(it.bytes)) }

        val history = conversation.messages.map { it.toSpringMessage() }
        val promptText = text.ifBlank { "(geen tekst, kijk naar de foto's)" }

        val reply = gardenChatClient.prompt()
            .messages(history)
            .user { user ->
                user.text(promptText)
                if (media.isNotEmpty()) user.media(*media.toTypedArray())
            }
            .call()
            .content()
            ?: "Sorry, ik kon geen antwoord bedenken."

        val now = Instant.now()
        val userMessage = GardenMessage(
            id = UUID.randomUUID().toString(),
            role = GardenMessage.ROLE_USER,
            text = text,
            imageIds = imageIds,
            createdAt = now,
        )
        val assistantMessage = GardenMessage(
            id = UUID.randomUUID().toString(),
            role = GardenMessage.ROLE_ASSISTANT,
            text = reply,
            createdAt = now,
        )
        val updated = conversation.copy(messages = conversation.messages + userMessage + assistantMessage)
        conversations.save(updated)

        return ChatResult(conversationId = updated.id, reply = reply, messages = updated.messages)
    }
}
