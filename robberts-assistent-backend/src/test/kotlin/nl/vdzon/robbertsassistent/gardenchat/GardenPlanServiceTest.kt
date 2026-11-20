package nl.vdzon.robbertsassistent.gardenchat

import java.time.LocalDate
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GardenPlanServiceTest {
    /** Onthoudt de laatste vraag (tekst en aantal foto's) die naar de AI ging. */
    private class RecordingChatModel : ChatModel {
        var text = ""
        var mediaCount = 0
        override fun call(prompt: Prompt): ChatResponse {
            val user = prompt.instructions.filterIsInstance<UserMessage>().last()
            text = user.text
            mediaCount = user.media.size
            return ChatResponse(listOf(Generation(AssistantMessage("antwoord"))))
        }

        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
    }

    private val photos = InMemoryPhotoStorage()
    private val plan = GardenPlanService(InMemoryGardenPlanRepository(), photos)
    private val model = RecordingChatModel()
    private val chat = GardenChatService(ChatClient.builder(model).build(), InMemoryConversationRepository(), photos, plan)

    private fun photo(vararg bytes: Byte) = PhotoUpload(bytes, "image/jpeg")

    @Test
    fun `bak blijft binnen de plattegrond en krijgt een geschiedenis met foto's`() {
        val bed = plan.createBed(" Bak 1 ", x = 0.9, y = -0.2, width = 0.3, height = 0.01)
        assertEquals("Bak 1", bed.name)
        assertEquals(0.7, bed.x, 1e-9)
        assertEquals(0.0, bed.y)
        assertEquals(0.05, bed.height)

        plan.addEntry(bed.id, "Wortels gezaaid", listOf(photo(1)), LocalDate.parse("2026-04-01"))
        val updated = plan.addEntry(bed.id, "", listOf(photo(2), photo(3)), LocalDate.parse("2026-06-01"))
        assertEquals(2, updated.entries.size)
        assertEquals(2, plan.latestPhotos(bed.id).size)

        val without = plan.deleteEntry(bed.id, updated.entries.last().id)
        assertEquals(listOf("Wortels gezaaid"), without.entries.map { it.text })

        assertFailsWith<IllegalArgumentException> { plan.addEntry(bed.id, " ", emptyList(), null) }
        assertFailsWith<IllegalArgumentException> { plan.createBed(" ", 0.0, 0.0, 0.2, 0.2) }
        assertFailsWith<BedNotFoundException> { plan.bed("bestaat-niet") }

        plan.deleteBed(bed.id)
        assertEquals(emptyList(), plan.beds())
    }

    @Test
    fun `achtergrond van de plattegrond wordt bewaard met zijn verhouding`() {
        val saved = plan.setBackground(photo(9, 9), 1.5)
        assertEquals(1.5, plan.plan().aspectRatio)
        assertEquals(2, photos.load(saved.backgroundPhotoId)!!.bytes.size)
        assertFailsWith<IllegalArgumentException> { plan.setBackground(photo(1), 0.0) }
    }

    @Test
    fun `vraag over een bak krijgt de geschiedenis en de laatste foto's van die bak mee`() {
        val bed = plan.createBed("Bak 1", 0.1, 0.1, 0.2, 0.2)
        plan.addEntry(bed.id, "Wortels gezaaid", listOf(photo(1)), LocalDate.parse("2026-04-01"))
        plan.addEntry(bed.id, "Wortels geoogst, nu sla", emptyList(), LocalDate.parse("2026-07-01"))

        chat.chat(null, "Wat stond hier in het voorjaar?", emptyList(), bed.id)

        assertTrue(model.text.contains("moestuinbak \"Bak 1\""))
        assertTrue(model.text.indexOf("2026-07-01: Wortels geoogst, nu sla") < model.text.indexOf("2026-04-01: Wortels gezaaid"))
        assertTrue(model.text.endsWith("Vraag: Wat stond hier in het voorjaar?"))
        assertEquals(1, model.mediaCount, "de laatste foto van de bak gaat mee")

        // Met een eigen foto bij de vraag gaat alleen die mee.
        chat.chat(null, "Is dit ziek?", listOf(photo(7), photo(8)), bed.id)
        assertEquals(2, model.mediaCount)
    }

    @Test
    fun `algemene vraag krijgt per bak de laatste vastlegging mee`() {
        val a = plan.createBed("Bak A", 0.1, 0.1, 0.2, 0.2)
        plan.createBed("Lege bak", 0.5, 0.1, 0.2, 0.2)
        plan.addEntry(a.id, "Tomaten", emptyList(), LocalDate.parse("2026-05-01"))

        chat.chat(null, "Waar staan de tomaten?", emptyList())

        assertTrue(model.text.contains("- Bak A (2026-05-01): Tomaten"))
        assertTrue(!model.text.contains("Lege bak"))
        assertEquals(0, model.mediaCount)
    }
}
