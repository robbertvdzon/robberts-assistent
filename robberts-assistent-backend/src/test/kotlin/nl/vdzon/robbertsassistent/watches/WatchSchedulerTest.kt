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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchSchedulerTest {

    private class FixedChatModel(private val reply: String) : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = ChatResponse(listOf(Generation(AssistantMessage(reply))))
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
    }

    private class FailingFetcher(private val message: String) : WatchPageFetcher() {
        override fun fetchPlainText(url: String): String = error(message)
    }

    private class FixedFetcher(private val text: String) : WatchPageFetcher() {
        override fun fetchPlainText(url: String): String = text
    }

    private fun chatClient(reply: String): ChatClient = ChatClient.builder(FixedChatModel(reply)).build()

    private fun pushService() = PushService(
        FirebaseProvider(AppSecrets(rememberSecret = "x", googleClientId = "x", allowedEmails = setOf("robbert@vdzon.com"))),
        InMemoryFcmTokenStore(),
    )

    private fun watch(
        title: String = "aaltjes",
        frequency: WatchFrequency = WatchFrequency.DAGELIJKS,
        notifyOnFound: Boolean = true,
        status: WatchStatus = WatchStatus.NIET_GEVONDEN,
        active: Boolean = true,
    ) = Watch(
        id = "watch-1",
        title = title,
        url = "https://example.com",
        instruction = "geef een seintje als ze weer beschikbaar zijn",
        frequency = frequency,
        notifyOnFound = notifyOnFound,
        status = status,
        active = active,
    )

    @Test
    fun `transitie naar GEVONDEN met notifyOnFound zet de watch op inactief`() {
        val service = WatchesService(InMemoryWatchRepository())
        service.save(watch())
        val scheduler = WatchScheduler(service, FixedFetcher("weer op voorraad"), chatClient("GEVONDEN\nweer op voorraad"), pushService())

        scheduler.pollDueWatches()

        val updated = service.list().single()
        assertEquals(WatchStatus.GEVONDEN, updated.status)
        assertEquals("weer op voorraad", updated.statusText)
        assertFalse(updated.active)
    }

    @Test
    fun `blijft actief zolang niet gevonden`() {
        val service = WatchesService(InMemoryWatchRepository())
        service.save(watch())
        val scheduler = WatchScheduler(service, FixedFetcher("nog uitverkocht"), chatClient("NIET GEVONDEN\nnog uitverkocht"), pushService())

        scheduler.pollDueWatches()

        val updated = service.list().single()
        assertEquals(WatchStatus.NIET_GEVONDEN, updated.status)
        assertTrue(updated.active)
    }

    @Test
    fun `poll slaat een niet-actieve watch over`() {
        val service = WatchesService(InMemoryWatchRepository())
        service.save(watch(active = false, status = WatchStatus.GEVONDEN, notifyOnFound = true))
        val scheduler = WatchScheduler(service, FixedFetcher("weer op voorraad"), chatClient("GEVONDEN\nweer op voorraad"), pushService())

        scheduler.pollDueWatches()

        // Geen wijziging: lastCheckedAt blijft null, de watch werd niet opnieuw gepolld.
        assertEquals(null, service.list().single().lastCheckedAt)
    }

    @Test
    fun `poll slaat een watch over die nog niet aan de beurt is`() {
        val service = WatchesService(InMemoryWatchRepository())
        val alreadyChecked = watch().copy(lastCheckedAt = Instant.now())
        service.save(alreadyChecked)
        val scheduler = WatchScheduler(service, FixedFetcher("nog uitverkocht"), chatClient("GEVONDEN\nweer op voorraad"), pushService())

        scheduler.pollDueWatches()

        // DAGELIJKS: net gecontroleerd vandaag, dus niet opnieuw gepolld.
        assertEquals(WatchStatus.NIET_GEVONDEN, service.list().single().status)
    }

    @Test
    fun `falende paginaophaal voor één watch isoleert en beïnvloedt andere watches niet`() {
        val service = WatchesService(InMemoryWatchRepository())
        service.save(watch(title = "faalt"))
        service.save(
            Watch(
                id = "watch-2",
                title = "werkt",
                url = "https://example.com/2",
                instruction = "check iets",
                frequency = WatchFrequency.DAGELIJKS,
                notifyOnFound = true,
            ),
        )
        val scheduler = WatchScheduler(
            service,
            FailingFetcher("netwerkfout"),
            chatClient("GEVONDEN\nweer op voorraad"),
            pushService(),
        )

        // Mag niet gooien, ook al faalt de fetch voor beide watches; status blijft ongewijzigd.
        scheduler.pollDueWatches()

        val byTitle = service.list().associateBy { it.title }
        assertEquals(WatchStatus.NIET_GEVONDEN, byTitle.getValue("faalt").status)
        assertEquals(WatchStatus.ONBEKEND, byTitle.getValue("werkt").status)
        assertEquals(null, byTitle.getValue("faalt").lastCheckedAt)
        assertEquals(null, byTitle.getValue("werkt").lastCheckedAt)
    }

    @Test
    fun `AI-fout crasht de poller niet`() {
        val service = WatchesService(InMemoryWatchRepository())
        service.save(watch())
        val throwingChatClient = ChatClient.builder(object : ChatModel {
            override fun call(prompt: Prompt): ChatResponse = error("AI-fout")
            override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.error(IllegalStateException("AI-fout"))
        }).build()
        val scheduler = WatchScheduler(service, FixedFetcher("tekst"), throwingChatClient, pushService())

        scheduler.pollDueWatches()

        assertEquals(WatchStatus.NIET_GEVONDEN, service.list().single().status)
    }
}
