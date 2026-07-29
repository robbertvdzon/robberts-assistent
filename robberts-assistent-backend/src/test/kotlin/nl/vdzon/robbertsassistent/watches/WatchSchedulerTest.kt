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
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchSchedulerTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(WatchScheduler.ZONE).toInstant()

    private fun watch(
        frequency: WatchFrequency,
        lastCheckedAt: Instant? = null,
        active: Boolean = true,
    ) = Watch(
        id = "1",
        title = "t",
        url = "https://a",
        instruction = "i",
        frequency = frequency,
        active = active,
        lastCheckedAt = lastCheckedAt,
    )

    // 2026-07-29 is een woensdag, 2026-08-01 een zaterdag, 2026-08-02 een zondag.

    @Test
    fun `gepauzeerde zoekopdracht is nooit aan de beurt`() {
        val now = at(2026, 7, 29, 10)
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN, active = false), now))
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.DAGELIJKS, active = false), now))
    }

    @Test
    fun `kantooruren is aan de beurt binnen werktijd op een werkdag`() {
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 7, 29, 10, 30)))
    }

    @Test
    fun `kantooruren respecteert de uurranden 09 en 17`() {
        // 08:59 nog niet, 09:00 wel, 16:59 wel, 17:00 niet meer.
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 7, 29, 8, 59)))
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 7, 29, 9, 0)))
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 7, 29, 16, 59)))
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 7, 29, 17, 0)))
    }

    @Test
    fun `kantooruren slaat het weekend over`() {
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 8, 1, 10)))
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 8, 2, 10)))
    }

    @Test
    fun `kantooruren controleert hoogstens een keer per klokuur`() {
        val lastCheck = at(2026, 7, 29, 10, 5)
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN, lastCheck), at(2026, 7, 29, 10, 55)))
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN, lastCheck), at(2026, 7, 29, 11, 0)))
    }

    @Test
    fun `dagelijks controleert hoogstens een keer per kalenderdag`() {
        val lastCheck = at(2026, 7, 29, 2)
        assertFalse(WatchScheduler.isDue(watch(WatchFrequency.DAGELIJKS, lastCheck), at(2026, 7, 29, 23, 59)))
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.DAGELIJKS, lastCheck), at(2026, 7, 30, 0, 1)))
    }

    @Test
    fun `dagelijks negeert weekend en kantooruren`() {
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.DAGELIJKS), at(2026, 8, 1, 3)))
    }

    @Test
    fun `nooit gecontroleerd is altijd aan de beurt binnen het venster`() {
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.KANTOORUREN), at(2026, 7, 29, 9, 30)))
        assertTrue(WatchScheduler.isDue(watch(WatchFrequency.DAGELIJKS), at(2026, 7, 29, 9, 30)))
    }

    // -- pollDue -----------------------------------------------------------------

    private class FixedChatModel(private val reply: String) : ChatModel {
        override fun call(prompt: Prompt): ChatResponse = ChatResponse(listOf(Generation(AssistantMessage(reply))))
        override fun stream(prompt: Prompt): Flux<ChatResponse> = Flux.just(call(prompt))
    }

    /** Faalt op de opgegeven URL's, geeft anders een pagina terug. */
    private class PollTestService(
        repository: WatchRepository,
        private val failingUrls: Set<String>,
    ) : WatchesService(
        repository,
        ChatClient.builder(FixedChatModel("NIET GEVONDEN\nnog steeds uitverkocht")).build(),
        PushService(
            FirebaseProvider(
                AppSecrets(rememberSecret = "x", googleClientId = "x", allowedEmails = setOf("robbert@vdzon.com")),
            ),
            InMemoryFcmTokenStore(),
        ),
    ) {
        override fun fetchPageText(url: String): String =
            if (url in failingUrls) error("kapotte pagina") else "<p>uitverkocht</p>"
    }

    @Test
    fun `pollDue controleert de zoekopdrachten die aan de beurt zijn en slaat de rest over`() {
        val repository = InMemoryWatchRepository()
        val service = PollTestService(repository, emptySet())
        repository.save(watch(WatchFrequency.DAGELIJKS).copy(id = "due"))
        repository.save(watch(WatchFrequency.DAGELIJKS, lastCheckedAt = Instant.now()).copy(id = "recent"))
        repository.save(watch(WatchFrequency.DAGELIJKS, active = false).copy(id = "gepauzeerd"))

        WatchScheduler(service).pollDue()

        assertEquals("nog steeds uitverkocht", repository.find("due")!!.lastStatus)
        assertNull(repository.find("recent")!!.lastStatus)
        assertNull(repository.find("gepauzeerd")!!.lastCheckedAt)
    }

    @Test
    fun `een falende zoekopdracht blokkeert de overige niet`() {
        val repository = InMemoryWatchRepository()
        val service = PollTestService(repository, failingUrls = setOf("https://kapot"))
        repository.save(watch(WatchFrequency.DAGELIJKS).copy(id = "kapot", url = "https://kapot"))
        repository.save(watch(WatchFrequency.DAGELIJKS).copy(id = "ok"))

        WatchScheduler(service).pollDue()

        assertNotNull(repository.find("kapot")!!.lastError)
        assertNull(repository.find("ok")!!.lastError)
        assertEquals("nog steeds uitverkocht", repository.find("ok")!!.lastStatus)
    }
}
