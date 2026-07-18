package nl.vdzon.robbertsassistent.gardenchat

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GardenChatServiceTest {
    private val client = ChatClient.builder(MockChatModel()).build()
    private val photos = InMemoryPhotoStorage()
    private val service = GardenChatService(client, InMemoryConversationRepository(), photos)

    @Test
    fun `chat maakt een conversatie met vraag- en antwoordbericht`() {
        val result = service.chat(null, "wat is dit voor plant?", emptyList())

        assertTrue(result.conversationId.isNotBlank())
        assertEquals(2, result.messages.size)
        assertEquals(GardenMessage.ROLE_USER, result.messages[0].role)
        assertEquals(GardenMessage.ROLE_ASSISTANT, result.messages[1].role)
        assertTrue(result.reply.isNotBlank())
    }

    @Test
    fun `vervolgvraag gebruikt dezelfde conversatie en groeit de historie`() {
        val first = service.chat(null, "hoi", emptyList())
        val second = service.chat(first.conversationId, "en nu?", emptyList())

        assertEquals(first.conversationId, second.conversationId)
        assertEquals(4, second.messages.size)
    }

    @Test
    fun `foto's worden opgeslagen en als imageIds aan het gebruikersbericht gehangen`() {
        val result = service.chat(null, "kijk", listOf(PhotoUpload(byteArrayOf(1, 2, 3), "image/jpeg")))

        val imageIds = result.messages[0].imageIds
        assertEquals(1, imageIds.size)
        assertEquals(3, photos.load(imageIds.first())!!.bytes.size)
    }
}
