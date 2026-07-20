package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.config.AppSecrets
import org.slf4j.LoggerFactory
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
 * gesprekshistorie + de actuele geheugen-tekst (als contextprefix) naar de assistent-[ChatClient]
 * (met tools voor notities/wind/reminders/alarms/agenda/docs/push, zie `assistant.ai.AiConfig`),
 * en bewaart zowel het vraag- als het antwoordbericht in de conversatie. Na de eerste uitwisseling
 * van een gesprek wordt een titel verzonnen — via een lichte, aparte AI-aanroep, of (zonder echte
 * AI, `RA_MOCK_AI`) een deterministische placeholder op basis van de eerste woorden van de vraag.
 * Na elke beurt (niet onder `RA_MOCK_AI`) wordt het gebruiker-brede geheugen bijgewerkt via een
 * losse, stil falende AI-aanroep, zie [updateMemoryFromExchange].
 */
@Service
class AssistantService(
    private val assistantChatClient: ChatClient,
    @Qualifier("titleChatClient") private val titleChatClient: ChatClient,
    @Qualifier("memoryChatClient") private val memoryChatClient: ChatClient,
    private val secrets: AppSecrets,
    @Qualifier("assistantConversationRepository") private val conversations: ConversationRepository,
    @Qualifier("assistantPhotoStorage") private val photos: PhotoStorage,
    @Qualifier("assistantMemoryRepository") private val memory: MemoryRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
        val memoryText = memory.current()
        val promptText = buildPromptText(text, memoryText)

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

        if (!secrets.effectiveMockAi) {
            updateMemoryFromExchange(text, reply, memoryText)
        }

        return ChatResult(conversationId = updated.id, title = title, reply = reply, messages = updatedMessages)
    }

    fun currentMemory(): String = memory.current()

    fun saveMemory(text: String): String = memory.update(text)

    /** Geeft de actuele geheugen-tekst als contextprefix mee aan de vraag, zodat die in de AI-prompt terechtkomt. */
    private fun buildPromptText(text: String, memoryText: String): String {
        val question = text.ifBlank { "(geen tekst, kijk naar de foto's)" }
        if (memoryText.isBlank()) return question
        return "Bekende context over Robbert (geheugen):\n$memoryText\n\nVraag: $question"
    }

    /**
     * Losse, lichte AI-aanroep die op basis van de huidige geheugen-tekst en de laatste
     * vraag/antwoord-uitwisseling een bijgewerkte volledige geheugen-tekst teruggeeft (zie
     * `AiConfig.MEMORY_SYSTEM_PROMPT`), en die direct opslaat. Faalt stil (geheugen blijft
     * ongewijzigd) bij een fout of een leeg antwoord.
     */
    private fun updateMemoryFromExchange(question: String, answer: String, currentText: String) {
        runCatching {
            val memoryContext = currentText.ifBlank { "(nog geen geheugen)" }
            val prompt = "Laatste uitwisseling:\nVraag: $question\nAntwoord: $answer\n\n" +
                "Huidig geheugen:\n$memoryContext"
            val content = memoryChatClient.prompt().user(prompt).call().content()?.trim()
            if (content.isNullOrBlank()) return@runCatching
            memory.update(content)
        }.onFailure { logger.warn("Geheugen-update mislukt, geheugen blijft ongewijzigd", it) }
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
