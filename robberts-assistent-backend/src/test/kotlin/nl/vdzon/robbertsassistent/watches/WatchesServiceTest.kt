package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.assistant.ai.MockChatModel
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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchesServiceTest {

    private class FixedChatModel(private val reply: String) : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = ChatResponse(listOf(Generation(AssistantMessage(reply))))
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
    }

    private class ThrowingChatModel : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = error("AI-fout")
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.error(IllegalStateException("AI-fout"))
    }

    // PushService zonder Firebase-config → sendToAll is toch al een no-op; de test-subclass telt
    // de pushes zodat "precies één push bij de omslag" écht getoetst wordt.
    private fun pushService() = PushService(
        FirebaseProvider(
            AppSecrets(rememberSecret = "x", googleClientId = "x", allowedEmails = setOf("robbert@vdzon.com")),
        ),
        InMemoryFcmTokenStore(),
    )

    /** Vervangt de netwerkcall en telt de verstuurde pushes. */
    private class TestWatchesService(
        repository: WatchRepository,
        chatModel: ChatModel,
        pushService: PushService,
        private val page: () -> String,
    ) : WatchesService(repository, ChatClient.builder(chatModel).build(), pushService) {
        val pushes = mutableListOf<Watch>()

        override fun fetchPageText(url: String): String = page()

        override fun sendFoundPush(watch: Watch) {
            pushes += watch
        }
    }

    private fun service(
        reply: String = "NIET GEVONDEN\nnog steeds uitverkocht",
        chatModel: ChatModel = FixedChatModel(reply),
        page: () -> String = { "<html><body>uitverkocht</body></html>" },
        repository: WatchRepository = InMemoryWatchRepository(),
    ) = TestWatchesService(repository, chatModel, pushService(), page)

    private fun Watch.stored(service: WatchesService) = service.find(id)!!

    // -- CRUD --------------------------------------------------------------------

    @Test
    fun `create slaat op en list geeft de nieuwste bovenaan`() {
        val service = service()
        service.create("oud", "https://a", "let op a", WatchFrequency.DAGELIJKS, true, Instant.ofEpochSecond(100))
        service.create("nieuw", "https://b", "let op b", WatchFrequency.KANTOORUREN, false, Instant.ofEpochSecond(200))

        val titles = service.list().map { it.title }

        assertEquals(listOf("nieuw", "oud"), titles)
        assertEquals(WatchFrequency.KANTOORUREN, service.list().first().frequency)
        assertFalse(service.list().first().pushOnFound)
    }

    @Test
    fun `update wijzigt de velden inclusief pauzeren, en geeft null voor een onbekende id`() {
        val service = service()
        val watch = service.create("titel", "https://a", "instructie", WatchFrequency.DAGELIJKS, true)

        val updated = service.update(
            id = watch.id,
            title = "andere titel",
            url = "https://b",
            instruction = "andere instructie",
            frequency = WatchFrequency.KANTOORUREN,
            pushOnFound = false,
            active = false,
        )

        assertNotNull(updated)
        assertEquals("andere titel", updated.title)
        assertEquals(WatchFrequency.KANTOORUREN, updated.frequency)
        assertFalse(updated.active)
        assertFalse(updated.pushOnFound)
        assertNull(service.update("onbekend", "t", "u", "i", WatchFrequency.DAGELIJKS, true, true))
    }

    @Test
    fun `delete verwijdert de zoekopdracht`() {
        val service = service()
        val watch = service.create("titel", "https://a", "instructie", WatchFrequency.DAGELIJKS, true)

        service.delete(watch.id)

        assertTrue(service.list().isEmpty())
    }

    // -- Statusparsing -----------------------------------------------------------

    @Test
    fun `herkend antwoord GEVONDEN levert found met de statuszin`() {
        val assessment = WatchesService.parseAssessment("GEVONDEN\nnu weer op voorraad")

        assertTrue(assessment.found)
        assertEquals("nu weer op voorraad", assessment.status)
    }

    @Test
    fun `NIET GEVONDEN wordt niet als GEVONDEN gelezen`() {
        val assessment = WatchesService.parseAssessment("NIET GEVONDEN\nnog steeds uitverkocht")

        assertFalse(assessment.found)
        assertEquals("nog steeds uitverkocht", assessment.status)
    }

    @Test
    fun `niet-herkend antwoord levert niet-gevonden met de ruwe tekst als status`() {
        val assessment = WatchesService.parseAssessment("Mock-antwoord (geen echte AI in deze omgeving)")

        assertFalse(assessment.found)
        assertEquals("Mock-antwoord (geen echte AI in deze omgeving)", assessment.status)
    }

    @Test
    fun `leeg antwoord levert niet-gevonden met een neutrale status`() {
        val assessment = WatchesService.parseAssessment("   ")

        assertFalse(assessment.found)
        assertEquals("Kon niet bepalen.", assessment.status)
    }

    @Test
    fun `ontbrekende statusregel krijgt een standaardzin`() {
        assertEquals("Gevonden.", WatchesService.parseAssessment("GEVONDEN").status)
        assertEquals("Nog niet gevonden.", WatchesService.parseAssessment("NIET GEVONDEN").status)
    }

    @Test
    fun `htmlToPlainText verwijdert script, style en tags en comprimeert whitespace`() {
        val html = """
            <html><head><style>body{color:red}</style><script>var x = 1;</script></head>
            <body><h1>Titel</h1><p>Nu &amp; later   op voorraad</p></body></html>
        """.trimIndent()

        val text = WatchesService.htmlToPlainText(html)

        assertEquals("Titel Nu & later op voorraad", text)
    }

    @Test
    fun `htmlToPlainText topt lange pagina's af`() {
        val text = WatchesService.htmlToPlainText("<p>" + "a".repeat(20_000) + "</p>")

        assertEquals(6000, text.length)
    }

    // -- Check + push ------------------------------------------------------------

    @Test
    fun `onder mock-AI levert een check een deterministische niet-gevonden uitkomst zonder push`() {
        val service = service(chatModel = MockChatModel())
        val watch = service.create("aaltjes", "https://a", "meld als op voorraad", WatchFrequency.DAGELIJKS, true)

        val checked = service.check(watch, Instant.ofEpochSecond(1000))

        assertFalse(checked.found)
        assertTrue(checked.active)
        assertTrue(service.pushes.isEmpty())
        assertEquals(Instant.ofEpochSecond(1000), checked.lastCheckedAt)
        assertTrue(checked.lastStatus!!.startsWith("Mock-antwoord"))
    }

    @Test
    fun `omslag naar gevonden stuurt precies een push en rondt de zoekopdracht af`() {
        val service = service(reply = "GEVONDEN\nnu weer op voorraad")
        val watch = service.create("aaltjes", "https://a", "meld als op voorraad", WatchFrequency.DAGELIJKS, true)

        val checked = service.check(watch)

        assertTrue(checked.found)
        assertFalse(checked.active) // afgerond, dus de poller slaat 'm over
        assertEquals(1, service.pushes.size)
        assertEquals("aaltjes", service.pushes.single().title)
        assertEquals("nu weer op voorraad", service.pushes.single().lastStatus)

        // Nog een keer controleren (bv. handmatig) → geen tweede push.
        service.check(checked.stored(service))
        assertEquals(1, service.pushes.size)
    }

    @Test
    fun `een al-gevonden zoekopdracht die gevonden blijft geeft geen nieuwe push`() {
        val repository = InMemoryWatchRepository()
        val service = service(reply = "GEVONDEN\nnog steeds beschikbaar", repository = repository)
        val watch = repository.save(
            Watch(id = "1", title = "t", url = "https://a", instruction = "i", found = true, lastStatus = "was al zo"),
        )

        val checked = service.check(watch)

        assertTrue(checked.found)
        assertTrue(service.pushes.isEmpty())
    }

    @Test
    fun `zonder pushOnFound wordt er niet gepusht, maar wel afgerond`() {
        val service = service(reply = "GEVONDEN\nnu weer op voorraad")
        val watch = service.create("aaltjes", "https://a", "meld als", WatchFrequency.DAGELIJKS, pushOnFound = false)

        val checked = service.check(watch)

        assertTrue(checked.found)
        assertFalse(checked.active)
        assertTrue(service.pushes.isEmpty())
    }

    // -- Foutafhandeling ---------------------------------------------------------

    @Test
    fun `een falende pagina-fetch zet alleen lastError en laat de vorige status staan`() {
        val repository = InMemoryWatchRepository()
        val service = service(page = { error("netwerk plat") }, repository = repository)
        val watch = repository.save(
            Watch(id = "1", title = "t", url = "https://a", instruction = "i", lastStatus = "nog steeds uitverkocht"),
        )

        val checked = service.check(watch, Instant.ofEpochSecond(500))

        assertEquals("netwerk plat", checked.lastError)
        assertEquals("nog steeds uitverkocht", checked.lastStatus)
        assertFalse(checked.found)
        assertTrue(checked.active)
        assertEquals(Instant.ofEpochSecond(500), checked.lastCheckedAt)
        assertTrue(service.pushes.isEmpty())
    }

    @Test
    fun `een falende AI-call zet alleen lastError en veroorzaakt geen push`() {
        val service = service(chatModel = ThrowingChatModel())
        val watch = service.create("t", "https://a", "i", WatchFrequency.DAGELIJKS, true)

        val checked = service.check(watch)

        assertNotNull(checked.lastError)
        assertTrue(service.pushes.isEmpty())
        assertTrue(checked.active)
    }

    @Test
    fun `een geslaagde check na een fout wist lastError`() {
        var fail = true
        val service = service(
            reply = "NIET GEVONDEN\nnog steeds uitverkocht",
            page = { if (fail) error("even stuk") else "<p>uitverkocht</p>" },
        )
        val watch = service.create("t", "https://a", "i", WatchFrequency.DAGELIJKS, true)

        val failed = service.check(watch)
        assertNotNull(failed.lastError)

        fail = false
        val recovered = service.check(failed)

        assertNull(recovered.lastError)
        assertEquals("nog steeds uitverkocht", recovered.lastStatus)
    }
}
