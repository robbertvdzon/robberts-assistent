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

data class MemoryItemDto(
    val id: String,
    val text: String,
    val updatedAt: String,
)

data class MemoryItemRequest(val text: String = "")

fun MemoryItem.toDto() = MemoryItemDto(id = id, text = text, updatedAt = updatedAt.toString())
