package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.config.AppSecrets
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
data class ChatResult(
    val conversationId: String,
    val title: String,
    val reply: String,
    val messages: List<ConversationMessage>,
)

/**
 * Kern van de persistente assistent-chat: bewaart de foto's, stuurt tekst + foto's + de volledige
 * gesprekshistorie naar de assistent-[ChatClient] (met tools voor notities/wind/reminders/alarms/
 * agenda/docs/push, zie `assistant.ai.AiConfig`), en bewaart zowel het vraag- als het
 * antwoordbericht in de conversatie. Na de eerste uitwisseling van een gesprek wordt een titel
 * verzonnen — via een lichte, aparte AI-aanroep, of (zonder echte AI, `RA_MOCK_AI`) een
 * deterministische placeholder op basis van de eerste woorden van de vraag.
 */
@Service
class AssistantService(
    private val assistantChatClient: ChatClient,
    @Qualifier("titleChatClient") private val titleChatClient: ChatClient,
    private val secrets: AppSecrets,
    @Qualifier("assistantConversationRepository") private val conversations: ConversationRepository,
    @Qualifier("assistantPhotoStorage") private val photos: PhotoStorage,
) {
    fun listConversations(includeArchived: Boolean = false, limit: Int? = null, offset: Int = 0): List<Conversation> =
        conversations.listAll(includeArchived, limit, offset)

    fun conversation(id: String): Conversation? = conversations.findById(id)

    /** Zet `archived=true`. `null` als het gesprek niet bestaat. */
    fun archiveConversation(id: String): Conversation? = setArchived(id, true)

    /** Zet `archived=false`. `null` als het gesprek niet bestaat. */
    fun unarchiveConversation(id: String): Conversation? = setArchived(id, false)

    private fun setArchived(id: String, archived: Boolean): Conversation? {
        val conversation = conversations.findById(id) ?: return null
        return conversations.save(conversation.copy(archived = archived))
    }

    /**
     * Verwijdert een gesprek en, best-effort, de bijbehorende foto's (een foto-opruimfout
     * blokkeert de delete niet). `false` als het gesprek niet bestaat.
     */
    fun deleteConversation(id: String): Boolean {
        val conversation = conversations.findById(id) ?: return false
        conversation.messages.flatMap { it.imageIds }.forEach { imageId ->
            runCatching { photos.delete(imageId) }
        }
        conversations.delete(id)
        return true
    }

    fun chat(conversationId: String?, text: String, uploads: List<PhotoUpload>): ChatResult {
        val conversation = conversationId?.let { conversations.findById(it) } ?: conversations.create()
        val isFirstExchange = conversation.messages.isEmpty()

        // Foto's opslaan (voor weergave/historie) én als Media klaarzetten voor de AI (deze beurt).
        val imageIds = uploads.map { photos.store(it.bytes, it.contentType) }
        val media = uploads.map { Media(MimeTypeUtils.parseMimeType(it.contentType), ByteArrayResource(it.bytes)) }

        val history = conversation.messages.map { it.toSpringMessage() }
        val promptText = text.ifBlank { "(geen tekst, kijk naar de foto's)" }

        val reply = assistantChatClient.prompt()
            .messages(history)
            .user { user ->
                user.text(promptText)
                if (media.isNotEmpty()) user.media(*media.toTypedArray())
            }
            .call()
            .content()
            ?: "Sorry, ik kon geen antwoord bedenken."

        val now = Instant.now()
        val userMessage = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = ConversationMessage.ROLE_USER,
            text = text,
            imageIds = imageIds,
            createdAt = now,
        )
        val assistantMessage = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = ConversationMessage.ROLE_ASSISTANT,
            text = reply,
            createdAt = now,
        )
        val updatedMessages = conversation.messages + userMessage + assistantMessage
        val title = conversation.title?.takeIf { it.isNotBlank() }
            ?: if (isFirstExchange) generateTitle(text, reply) else placeholderTitle(text)

        val updated = conversation.copy(messages = updatedMessages, title = title, updatedAt = now)
        conversations.save(updated)

        return ChatResult(conversationId = updated.id, title = title, reply = reply, messages = updatedMessages)
    }

    private fun placeholderTitle(question: String): String =
        question.trim().ifBlank { "Nieuw gesprek" }.take(TITLE_MAX_LENGTH)

    /** Zonder echte AI (`RA_MOCK_AI`) een deterministische placeholder; anders een korte AI-titel. */
    private fun generateTitle(question: String, answer: String): String {
        val placeholder = placeholderTitle(question)
        if (secrets.effectiveMockAi) return placeholder
        return runCatching {
            titleChatClient.prompt()
                .user("Vraag: $question\nAntwoord: $answer")
                .call()
                .content()
                ?.trim()
                ?.trim('"')
                ?.take(TITLE_MAX_LENGTH)
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: placeholder
    }

    private companion object {
        const val TITLE_MAX_LENGTH = 60
    }
}
