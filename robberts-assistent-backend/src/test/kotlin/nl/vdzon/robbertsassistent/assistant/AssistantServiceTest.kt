package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Draait tegen [MockChatModel] i.p.v. een echte OpenAI-call — deterministisch, geen netwerk/kosten.
 * Toolgedrag (notities/wind) wordt apart getest op `NotesTools`/`WindTools` zelf; het mock-model
 * roept bewust geen tools aan (zie de class-doc van [MockChatModel]).
 */
class AssistantServiceTest {
    private val photos = InMemoryPhotoStorage()

    private fun newService(mockAi: Boolean = true): AssistantService {
        val client = ChatClient.builder(MockChatModel()).build()
        val secrets = AppSecrets(
            rememberSecret = "test-remember-secret",
            googleClientId = "test-client-id.apps.googleusercontent.com",
            allowedEmails = setOf("robbert@vdzon.com"),
            mockAi = mockAi,
        )
        return AssistantService(client, client, secrets, InMemoryConversationRepository(), photos)
    }

    @Test
    fun `chat maakt een gesprek met vraag- en antwoordbericht`() {
        val service = newService()

        val result = service.chat(null, "wat is de wind", emptyList())

        assertTrue(result.conversationId.isNotBlank())
        assertEquals(2, result.messages.size)
        assertEquals(ConversationMessage.ROLE_USER, result.messages[0].role)
        assertEquals(ConversationMessage.ROLE_ASSISTANT, result.messages[1].role)
        assertTrue(result.reply.contains("wat is de wind"), "verwacht dat het mock-antwoord de vraag citeert: ${result.reply}")
    }

    @Test
    fun `vervolgvraag in hetzelfde gesprek gebruikt dezelfde conversatie en groeit de historie`() {
        val service = newService()

        val first = service.chat(null, "hoi", emptyList())
        val second = service.chat(first.conversationId, "en nu?", emptyList())

        assertEquals(first.conversationId, second.conversationId)
        assertEquals(4, second.messages.size)
    }

    @Test
    fun `nieuw gesprek zonder conversationId start zonder kennis van andere gesprekken`() {
        val service = newService()

        val first = service.chat(null, "hoi", emptyList())
        val other = service.chat(null, "iets heel anders", emptyList())

        assertNotEquals(first.conversationId, other.conversationId)
        assertEquals(2, other.messages.size)
    }

    @Test
    fun `krijgt na de eerste uitwisseling een titel, ook zonder echte AI`() {
        val service = newService(mockAi = true)

        val result = service.chat(null, "wat is de wind vandaag", emptyList())

        assertTrue(result.title.isNotBlank())
        assertEquals(result.title, service.conversation(result.conversationId)!!.title)
    }

    @Test
    fun `titel blijft gelijk bij een vervolgvraag`() {
        val service = newService()

        val first = service.chat(null, "hoi", emptyList())
        val second = service.chat(first.conversationId, "en nu?", emptyList())

        assertEquals(first.title, second.title)
    }

    @Test
    fun `foto's worden opgeslagen en als imageIds aan het gebruikersbericht gehangen`() {
        val service = newService()

        val result = service.chat(null, "kijk", listOf(PhotoUpload(byteArrayOf(1, 2, 3), "image/jpeg")))

        val imageIds = result.messages[0].imageIds
        assertEquals(1, imageIds.size)
    }

    @Test
    fun `listConversations geeft alle gesprekken terug`() {
        val service = newService()

        service.chat(null, "eerste", emptyList())
        service.chat(null, "tweede", emptyList())

        assertEquals(2, service.listConversations().size)
    }

    @Test
    fun `listConversations geeft limit en offset door aan de repository`() {
        val service = newService()

        service.chat(null, "eerste", emptyList())
        service.chat(null, "tweede", emptyList())
        service.chat(null, "derde", emptyList())

        assertEquals(2, service.listConversations(limit = 2).size)
        assertEquals(1, service.listConversations(limit = 2, offset = 2).size)
    }

    @Test
    fun `archiveConversation zet archived op true en sluit het gesprek uit van listConversations`() {
        val service = newService()
        val conversation = service.chat(null, "verberg mij", emptyList())

        val archived = service.archiveConversation(conversation.conversationId)

        assertEquals(true, archived?.archived)
        assertTrue(service.listConversations().none { it.id == conversation.conversationId })
        assertTrue(service.listConversations(includeArchived = true).any { it.id == conversation.conversationId })
    }

    @Test
    fun `unarchiveConversation zet archived terug op false`() {
        val service = newService()
        val conversation = service.chat(null, "verberg mij", emptyList())
        service.archiveConversation(conversation.conversationId)

        val unarchived = service.unarchiveConversation(conversation.conversationId)

        assertEquals(false, unarchived?.archived)
        assertTrue(service.listConversations().any { it.id == conversation.conversationId })
    }

    @Test
    fun `archiveConversation geeft null terug voor een onbekend gesprek`() {
        val service = newService()

        assertEquals(null, service.archiveConversation("onbekend"))
    }

    @Test
    fun `deleteConversation verwijdert het gesprek en zijn foto's`() {
        val service = newService()
        val conversation = service.chat(null, "kijk", listOf(PhotoUpload(byteArrayOf(1, 2, 3), "image/jpeg")))
        val imageId = service.conversation(conversation.conversationId)!!.messages[0].imageIds.single()

        val deleted = service.deleteConversation(conversation.conversationId)

        assertTrue(deleted)
        assertEquals(null, service.conversation(conversation.conversationId))
        assertEquals(null, photos.load(imageId))
    }

    @Test
    fun `deleteConversation geeft false terug voor een onbekend gesprek`() {
        val service = newService()

        assertEquals(false, service.deleteConversation("onbekend"))
    }
}
