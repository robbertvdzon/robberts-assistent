package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.push.InMemoryFcmTokenStore
import nl.vdzon.robbertsassistent.push.PushService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchSchedulerTest {

    private fun appSecrets(mockAi: Boolean = true) = AppSecrets(
        rememberSecret = "x",
        googleClientId = "x",
        allowedEmails = setOf("robbert@vdzon.com"),
        mockAi = mockAi,
    )

    private fun pushService(secrets: AppSecrets = appSecrets()) = PushService(
        FirebaseProvider(secrets),
        InMemoryFcmTokenStore(),
    )

    private fun chatClient(): ChatClient {
        val mockModel = object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse {
                return ChatResponse(listOf(Generation(AssistantMessage("GEVONDEN\nDit is de uitleg."))))
            }
            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
        }
        return ChatClient.builder(mockModel).build()
    }

    @Test
    fun `parseAiResponse herkent GEVONDEN`() {
        val response = "GEVONDEN\nDe tekst bevat wat je zoekt."
        val (status, text) = parseAiResponse(response)

        assertEquals(WatchStatus.GEVONDEN, status)
        assertEquals("De tekst bevat wat je zoekt.", text)
    }

    @Test
    fun `parseAiResponse herkent NIET GEVONDEN`() {
        val response = "NIET GEVONDEN\nNiets gevonden op de pagina."
        val (status, text) = parseAiResponse(response)

        assertEquals(WatchStatus.NIET_GEVONDEN, status)
        assertEquals("Niets gevonden op de pagina.", text)
    }

    @Test
    fun `parseAiResponse valt terug op ONBEKEND bij onverwacht formaat`() {
        val response = "Ik weet het niet zeker."
        val (status, _) = parseAiResponse(response)

        assertEquals(WatchStatus.ONBEKEND, status)
    }

    @Test
    fun `htmlToPlainText verwijdert tags en scripts`() {
        val html = """
            <html>
            <head><script>alert('x');</script></head>
            <body><p>Hello <b>World</b>!</p></body>
            </html>
        """.trimIndent()

        val plain = htmlToPlainText(html)

        assertTrue(plain.contains("Hello"))
        assertTrue(plain.contains("World"))
        assertFalse(plain.contains("<"))
        assertFalse(plain.contains("script"))
        assertFalse(plain.contains("alert"))
    }

    @Test
    fun `htmlToPlainText decodeert entities`() {
        val html = "a &amp; b &lt; c &gt; d &quot;e&quot; f&nbsp;g"
        val plain = htmlToPlainText(html)

        assertEquals("a & b < c > d \"e\" f g", plain)
    }

    @Test
    fun `mock-modus geeft ONBEKEND status`() {
        val service = WatchesService(InMemoryWatchRepository())
        val watch = service.create("Test", "https://example.com", "test", WatchFrequency.DAGELIJKS)

        val secrets = appSecrets(mockAi = true)
        val scheduler = WatchScheduler(service, pushService(secrets), secrets, chatClient())

        scheduler.pollWatches()

        val updated = service.findById(watch.id)!!
        assertEquals(WatchStatus.ONBEKEND, updated.status)
        assertEquals("AI niet beschikbaar (mock-modus)", updated.statusText)
    }
}

private fun parseAiResponse(response: String): Pair<WatchStatus, String?> {
    val lines = response.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return WatchStatus.ONBEKEND to null
    val firstLine = lines[0].trim().uppercase()
    val status = when {
        firstLine.startsWith("GEVONDEN") -> WatchStatus.GEVONDEN
        firstLine.startsWith("NIET GEVONDEN") || firstLine.startsWith("NIET_GEVONDEN") -> WatchStatus.NIET_GEVONDEN
        else -> WatchStatus.ONBEKEND
    }
    val statusText = lines.getOrNull(1)?.trim()
    return status to statusText
}
