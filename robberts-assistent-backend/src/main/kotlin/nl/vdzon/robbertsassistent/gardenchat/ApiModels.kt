package nl.vdzon.robbertsassistent.gardenchat

data class GardenMessageDto(
    val id: String,
    val role: String,
    val text: String,
    val imageIds: List<String>,
    val createdAt: String,
)

data class GardenChatResponse(
    val conversationId: String,
    val reply: String,
    val messages: List<GardenMessageDto>,
)

data class GardenConversationResponse(
    val conversationId: String,
    val messages: List<GardenMessageDto>,
)

fun GardenMessage.toDto() = GardenMessageDto(
    id = id,
    role = role,
    text = text,
    imageIds = imageIds,
    createdAt = createdAt.toString(),
)
