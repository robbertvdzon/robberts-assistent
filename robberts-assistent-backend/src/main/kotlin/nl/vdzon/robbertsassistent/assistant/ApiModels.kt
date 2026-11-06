package nl.vdzon.robbertsassistent.assistant

data class ConversationMessageDto(
    val id: String,
    val role: String,
    val text: String,
    val imageIds: List<String>,
    val createdAt: String,
)

data class ConversationSummaryDto(
    val conversationId: String,
    val title: String,
    val updatedAt: String,
    val archived: Boolean,
)

data class AssistantChatResponse(
    val conversationId: String,
    val title: String,
    val reply: String,
    val messages: List<ConversationMessageDto>,
)

/** Antwoord op het starten van een asynchrone chat-beurt (HTTP 202). */
data class AssistantChatJobStarted(val jobId: String)

/**
 * Status van een asynchrone chat-beurt. [status] is RUNNING, DONE of FAILED; bij DONE zijn
 * [conversationId], [title], [reply] en [messages] gevuld, bij FAILED de [error].
 */
data class AssistantChatJobResponse(
    val jobId: String,
    val status: String,
    val conversationId: String? = null,
    val title: String? = null,
    val reply: String? = null,
    val messages: List<ConversationMessageDto> = emptyList(),
    val error: String? = null,
)

data class AssistantConversationResponse(
    val conversationId: String,
    val title: String,
    val messages: List<ConversationMessageDto>,
)

fun ConversationMessage.toDto() = ConversationMessageDto(
    id = id,
    role = role,
    text = text,
    imageIds = imageIds,
    createdAt = createdAt.toString(),
)

fun Conversation.toSummaryDto() = ConversationSummaryDto(
    conversationId = id,
    title = title?.takeIf { it.isNotBlank() } ?: "Nieuw gesprek",
    updatedAt = updatedAt.toString(),
    archived = archived,
)

data class MemoryResponse(val text: String)

data class MemoryUpdateRequest(val text: String = "")
