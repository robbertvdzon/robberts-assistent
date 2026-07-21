package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
import nl.vdzon.robbertsassistent.notes.InMemoryNotesRepository
import nl.vdzon.robbertsassistent.notes.NotesService
import nl.vdzon.robbertsassistent.reminders.InMemoryReminderRepository
import nl.vdzon.robbertsassistent.reminders.RemindersService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Draait tegen [MockChatModel] (deterministisch, geen netwerk), zelfde patroon als
 * `AssistantServiceTest`.
 */
class WeekTasksSectionProviderTest {

    private class ThrowingChatModel : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = error("AI-fout")
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.error(IllegalStateException("AI-fout"))
    }

    private class FixedChatModel(private val reply: String) : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = ChatResponse(listOf(Generation(AssistantMessage(reply))))
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
    }

    private fun provider(
        chatModel: ChatModel = MockChatModel(),
        remindersService: RemindersService = RemindersService(InMemoryReminderRepository()),
        notesService: NotesService = NotesService(InMemoryNotesRepository()),
    ) = WeekTasksSectionProvider(remindersService, notesService, ChatClient.builder(chatModel).build())

    @Test
    fun `section geeft het AI-antwoord terug`() {
        val section = provider(chatModel = FixedChatModel("Niets dringends deze week.")).section()

        assertEquals("week-tasks", section.key)
        assertEquals("Niets dringends deze week.", section.text)
    }

    @Test
    fun `section valt terug op een placeholder als de AI-call faalt`() {
        val section = provider(chatModel = ThrowingChatModel()).section()

        assertEquals("Kon de weektaken-samenvatting niet ophalen.", section.text)
    }

    @Test
    fun `shortSummary telt actieve reminders`() {
        val reminders = RemindersService(InMemoryReminderRepository())
        reminders.create("a", Instant.now())
        reminders.create("b", Instant.now())

        val summary = provider(remindersService = reminders).shortSummary()

        assertEquals("2 taken", summary)
    }

    @Test
    fun `deterministisch onder mock-ai`() {
        val a = provider().section().text
        val b = provider().section().text

        assertTrue(a.isNotBlank())
        assertEquals(a, b)
    }
}
