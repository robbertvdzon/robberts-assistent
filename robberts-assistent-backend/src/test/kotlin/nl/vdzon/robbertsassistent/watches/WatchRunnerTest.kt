package nl.vdzon.robbertsassistent.watches

import nl.vdzon.robbertsassistent.push.PushService
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRunnerTest {
    private class FakeFetcher(var error: Boolean = false) : WatchPageFetcher {
        var calls = 0
        override fun fetch(url: String): String {
            calls++
            if (error) error("netwerk/http-fout")
            return "paginatekst"
        }
    }

    private class FakeEvaluator(
        var assessment: WatchAssessment = WatchAssessment(false, "Nog niet gevonden."),
        var error: Boolean = false,
    ) : WatchEvaluator {
        var calls = 0
        override fun assess(instruction: String, pageText: String): WatchAssessment {
            calls++
            if (error) error("AI/antwoord-fout")
            return assessment
        }
    }

    private class FakePush : WatchPushNotifier {
        val sent = mutableListOf<Watch>()
        override fun found(watch: Watch) {
            sent += watch
        }
    }

    private fun instant(dateTime: String) =
        ZonedDateTime.of(LocalDateTime.parse(dateTime), WatchSchedule.zone).toInstant()

    private fun watch(notify: Boolean = true, frequency: WatchFrequency = WatchFrequency.KANTOORUREN) =
        Watch("1", "Kaarten", "https://example.com", "zoek twee kaarten", frequency, notify)

    @Test
    fun `succesvolle niet-gevonden beoordeling wordt opgeslagen`() {
        val repository = InMemoryWatchRepository().also { it.save(watch()) }
        val runner = WatchRunner(repository, FakeFetcher(), FakeEvaluator(), FakePush())
        val now = instant("2026-07-27T09:00:00")

        runner.poll(now)

        val updated = repository.all().single()
        assertEquals(WatchStatus.NIET_GEVONDEN, updated.status)
        assertEquals("Nog niet gevonden.", updated.statusDescription)
        assertEquals(now, updated.lastCheckedAt)
        assertTrue(updated.active)
    }

    @Test
    fun `netwerk- en ai-fouten geven onbekend en worden op volgend gepland moment opnieuw geprobeerd`() {
        val repository = InMemoryWatchRepository().also { it.save(watch()) }
        val fetcher = FakeFetcher(error = true)
        val evaluator = FakeEvaluator()
        val runner = WatchRunner(repository, fetcher, evaluator, FakePush())

        runner.poll(instant("2026-07-27T09:00:00"))
        assertEquals(WatchStatus.ONBEKEND, repository.all().single().status)
        assertTrue(repository.all().single().active)
        assertEquals(0, evaluator.calls)

        fetcher.error = false
        evaluator.error = true
        runner.poll(instant("2026-07-27T10:00:00"))
        assertEquals(WatchStatus.ONBEKEND, repository.all().single().status)

        evaluator.error = false
        runner.poll(instant("2026-07-27T11:00:00"))
        assertEquals(WatchStatus.NIET_GEVONDEN, repository.all().single().status)
        assertEquals(3, fetcher.calls)
    }

    @Test
    fun `eerste vondst deactiveert en pusht precies eenmaal`() {
        val repository = InMemoryWatchRepository().also { it.save(watch()) }
        val evaluator = FakeEvaluator(WatchAssessment(true, "Twee kaarten beschikbaar."))
        val push = FakePush()
        val runner = WatchRunner(repository, FakeFetcher(), evaluator, push)

        runner.poll(instant("2026-07-27T09:00:00"))
        runner.poll(instant("2026-07-27T10:00:00"))

        val updated = repository.all().single()
        assertEquals(WatchStatus.GEVONDEN, updated.status)
        assertFalse(updated.active)
        assertEquals(1, evaluator.calls)
        assertEquals(1, push.sent.size)
    }

    @Test
    fun `vondst zonder meldingsvoorkeur deactiveert zonder push`() {
        val repository = InMemoryWatchRepository().also { it.save(watch(notify = false)) }
        val push = FakePush()
        val runner = WatchRunner(
            repository,
            FakeFetcher(),
            FakeEvaluator(WatchAssessment(true, "Gevonden.")),
            push,
        )

        runner.poll(instant("2026-07-27T09:00:00"))

        assertFalse(repository.all().single().active)
        assertTrue(push.sent.isEmpty())
    }

    @Test
    fun `lopende controle slaat een gelijktijdig verwijderde watch niet opnieuw op`() {
        val repository = InMemoryWatchRepository().also { it.save(watch()) }
        val fetchStarted = CountDownLatch(1)
        val continueFetch = CountDownLatch(1)
        val fetcher = object : WatchPageFetcher {
            override fun fetch(url: String): String {
                fetchStarted.countDown()
                assertTrue(continueFetch.await(5, TimeUnit.SECONDS))
                return "paginatekst"
            }
        }
        val push = FakePush()
        val runner = WatchRunner(
            repository,
            fetcher,
            FakeEvaluator(WatchAssessment(true, "Gevonden.")),
            push,
        )
        val executor = Executors.newSingleThreadExecutor()
        val poll = executor.submit {
            runner.poll(instant("2026-07-27T09:00:00"))
        }

        try {
            assertTrue(fetchStarted.await(5, TimeUnit.SECONDS))
            repository.delete("1")
            continueFetch.countDown()
            poll.get(5, TimeUnit.SECONDS)
        } finally {
            continueFetch.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }

        assertTrue(repository.all().isEmpty())
        assertTrue(push.sent.isEmpty())
    }

    @Test
    fun `pushadapter verstuurt watch-type voor deeplink`() {
        val pushService = mock(PushService::class.java)
        val found = watch().copy(
            status = WatchStatus.GEVONDEN,
            statusDescription = "Twee kaarten beschikbaar.",
            active = false,
        )

        PushServiceWatchNotifier(pushService).found(found)

        verify(pushService).sendToAll(
            "Zoekopdracht gevonden",
            "Kaarten: Twee kaarten beschikbaar.",
            mapOf("type" to "watch"),
        )
    }
}
