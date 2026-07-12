package nl.vdzon.robbertsassistent.assistant

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draait tegen [MockChatModel] i.p.v. een echte OpenAI-call — deterministisch, geen netwerk/kosten.
 * Toolgedrag (notities/wind) wordt apart getest op `NotesTools`/`WindTools` zelf; het mock-model
 * roept bewust geen tools aan (zie de class-doc van [MockChatModel]).
 */
class AssistantServiceTest {
    private val service = AssistantService(ChatClient.builder(MockChatModel()).build())

    @Test
    fun `geeft het antwoord van het chat-model terug`() {
        val reply = service.reply("wat is de wind")

        assertTrue(reply.contains("wat is de wind"), "verwacht dat het mock-antwoord de vraag citeert: $reply")
    }

    @Test
    fun `werkt voor elk bericht`() {
        val reply = service.reply("iets heel anders")

        assertTrue(reply.isNotBlank())
    }
}
