package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import nl.vdzon.robbertsassistent.config.AppSecrets
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
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

    private fun newService(
        mockAi: Boolean = true,
        memory: MemoryRepository = InMemoryMemoryRepository(),
        memoryChatModel: ChatModel = MockChatModel(),
    ): AssistantService {
        val client = ChatClient.builder(MockChatModel()).build()
        val memoryClient = ChatClient.builder(memoryChatModel).build()
        val secrets = AppSecrets(
            rememberSecret = "test-remember-secret",
            googleClientId = "test-client-id.apps.googleusercontent.com",
            allowedEmails = setOf("robbert@vdzon.com"),
            // openAiApiKey moet gezet zijn, anders is effectiveMockAi altijd true ongeacht mockAi.
            openAiApiKey = if (mockAi) null else "test-openai-key",
            mockAi = mockAi,
        )
        return AssistantService(client, client, memoryClient, secrets, InMemoryConversationRepository(), photos, memory)
    }

    /** [ChatModel]-fake die een vast antwoord teruggeeft, voor het testen van de geheugen-update-flow. */
    private class FixedChatModel(private val reply: String) : ChatModel {
        var lastPrompt: Prompt? = null

        override fun call(prompt: Prompt): ChatResponse {
            lastPrompt = prompt
            return ChatResponse(listOf(Generation(AssistantMessage(reply))))
        }

        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
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

    @Test
    fun `createMemoryItem-updateMemoryItem-deleteMemoryItem beheren losse geheugen-items`() {
        val service = newService()

        val created = service.createMemoryItem("houdt van windsurfen")
        assertTrue(service.listMemory().any { it.id == created.id && it.text == "houdt van windsurfen" })

        val updated = service.updateMemoryItem(created.id, "houdt van kiten")
        assertEquals("houdt van kiten", updated?.text)
        assertEquals("houdt van kiten", service.listMemory().single { it.id == created.id }.text)

        assertEquals(null, service.updateMemoryItem("onbekend", "iets"))

        assertTrue(service.deleteMemoryItem(created.id))
        assertTrue(service.listMemory().none { it.id == created.id })
        assertEquals(false, service.deleteMemoryItem(created.id))
    }

    @Test
    fun `chat geeft de actuele geheugen-items als context mee aan de hoofd-ChatClient-aanroep`() {
        val memory = InMemoryMemoryRepository()
        memory.create("kite bij voorkeur boven 15 knopen wind")
        val service = newService(memory = memory)

        val result = service.chat(null, "wat vind ik van deze wind?", emptyList())

        assertTrue(
            result.reply.contains("kite bij voorkeur boven 15 knopen wind"),
            "verwacht dat het geheugen-item in de prompt naar de ChatClient terechtkomt: ${result.reply}",
        )
    }

    @Test
    fun `chat slaat de geheugen-update-AI-aanroep over onder RA_MOCK_AI`() {
        val memoryModel = FixedChatModel("nieuw feit")
        val service = newService(mockAi = true, memoryChatModel = memoryModel)

        service.chat(null, "onthoud dat ik van vissen houd", emptyList())

        assertEquals(null, memoryModel.lastPrompt, "de memoryChatClient mag niet aangeroepen zijn onder RA_MOCK_AI")
    }

    @Test
    fun `chat werkt het geheugen bij op basis van de AI-uitkomst (nieuwe, behouden en verwijderde items)`() {
        val memory = InMemoryMemoryRepository()
        val behouden = memory.create("Robbert woont in Heemskerk")
        memory.create("verouderd feit")
        val memoryModel = FixedChatModel("Robbert woont in Heemskerk\nRobbert houdt van vissen")
        val service = newService(mockAi = false, memory = memory, memoryChatModel = memoryModel)

        service.chat(null, "ik hou van vissen", emptyList())

        val items = memory.listAll()
        assertEquals(2, items.size)
        assertTrue(items.any { it.id == behouden.id && it.text == "Robbert woont in Heemskerk" })
        assertTrue(items.any { it.text == "Robbert houdt van vissen" })
        assertTrue(items.none { it.text == "verouderd feit" })
    }

    @Test
    fun `chat laat het geheugen ongewijzigd als de AI GEEN teruggeeft`() {
        val memory = InMemoryMemoryRepository()
        memory.create("blijft staan")
        val memoryModel = FixedChatModel("GEEN")
        val service = newService(mockAi = false, memory = memory, memoryChatModel = memoryModel)

        service.chat(null, "iets onbelangrijks", emptyList())

        assertTrue(memory.listAll().isEmpty())
    }

    @Test
    fun `chat laat het geheugen ongewijzigd als de geheugen-AI-aanroep faalt`() {
        val memory = InMemoryMemoryRepository()
        memory.create("blijft staan")
        val failingModel = object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse = throw RuntimeException("AI onbereikbaar")
            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.error(RuntimeException("AI onbereikbaar"))
        }
        val service = newService(mockAi = false, memory = memory, memoryChatModel = failingModel)

        service.chat(null, "iets", emptyList())

        assertEquals(listOf("blijft staan"), memory.listAll().map { it.text })
    }
}
