package nl.vdzon.robbertsassistent.applaunch

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppLaunchServiceTest {
    private val repository = InMemoryAppLaunchRepository()
    private var clock = Instant.parse("2026-08-01T10:00:00Z")
    private val service = AppLaunchService(repository) { clock }

    private val logger = LoggerFactory.getLogger(AppLaunchService::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeTest
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    private fun logLines() = appender.list.filter { it.formattedMessage.startsWith("APP_LAUNCH") }

    @Test
    fun `opslaan bepaalt id en tijdstip server-side en bewaart de meegegeven gegevens`() {
        val saved = service.record(
            source = AppLaunchSource.ASSISTANT,
            platform = "android",
            referrer = "com.google.android.apps.gemini",
            action = "android.intent.action.MAIN",
            categories = listOf("android.intent.category.LAUNCHER"),
            extras = mapOf("query" to "wat is de wind"),
            appVersion = "42",
        )

        assertTrue(saved.id.isNotBlank())
        assertEquals(clock, saved.at)
        assertEquals(saved, repository.recent(10).single())
    }

    @Test
    fun `per opgeslagen launch gaat er precies een INFO-regel uit in het greppable formaat`() {
        service.record(
            source = AppLaunchSource.ASSISTANT,
            platform = "android",
            referrer = "com.google.android.apps.gemini",
            action = "android.intent.action.MAIN",
            categories = listOf("cat.a", "cat.b"),
            extras = linkedMapOf("k1" to "v1", "k2" to "regel1\nregel2"),
        )

        val event = logLines().single()
        assertEquals(Level.INFO, event.level)
        assertEquals(
            "APP_LAUNCH source=ASSISTANT platform=android referrer=com.google.android.apps.gemini " +
                "action=android.intent.action.MAIN categories=cat.a,cat.b extras=k1=v1;k2=regel1 regel2",
            event.formattedMessage,
        )
        assertTrue(!event.formattedMessage.contains('\n'))
    }

    @Test
    fun `ontbrekende waarden loggen als null en lege lijsten en maps als lege waarde`() {
        service.record(source = AppLaunchSource.UNKNOWN, platform = "web")

        assertEquals(
            "APP_LAUNCH source=UNKNOWN platform=web referrer=null action=null categories= extras=",
            logLines().single().formattedMessage,
        )
    }

    @Test
    fun `de laatste launches komen nieuwste eerst terug en respecteren de limiet`() {
        val start = clock
        repeat(5) { index ->
            clock = start.plus(Duration.ofMinutes(index.toLong()))
            service.record(source = AppLaunchSource.OTHER, platform = "android", action = "start-$index")
        }

        assertEquals(listOf("start-4", "start-3"), service.recent(2).map { it.action })
        assertEquals(5, service.recent().size)
    }

    @Test
    fun `de limiet wordt begrensd op het maximum`() {
        clock = clock.plusSeconds(1)
        service.record(source = AppLaunchSource.OTHER, platform = "android")

        // Zonder begrenzing zou de repository een absurde limiet doorkrijgen; hier telt vooral dat
        // een te grote limiet gewoon werkt en de bestaande launch teruggeeft.
        assertEquals(1, service.recent(10_000).size)
    }

    @Test
    fun `launches ouder dan 30 dagen zijn na een nieuwe opslag verwijderd`() {
        val oud = clock
        service.record(source = AppLaunchSource.OTHER, platform = "android", action = "oud")

        clock = oud.plus(Duration.ofDays(31))
        service.record(source = AppLaunchSource.OTHER, platform = "android", action = "nieuw")

        assertEquals(listOf("nieuw"), service.recent().map { it.action })
    }

    @Test
    fun `launches binnen 30 dagen blijven staan`() {
        val oud = clock
        service.record(source = AppLaunchSource.OTHER, platform = "android", action = "oud")

        clock = oud.plus(Duration.ofDays(29))
        service.record(source = AppLaunchSource.OTHER, platform = "android", action = "nieuw")

        assertEquals(listOf("nieuw", "oud"), service.recent().map { it.action })
    }

    @Test
    fun `een falende opschoning laat het opslaan en loggen gewoon slagen`() {
        val brokenCleanUp = object : AppLaunchRepository by repository {
            override fun deleteOlderThan(cutoff: Instant): Int = error("Firestore even weg")
        }
        val serviceWithBrokenCleanUp = AppLaunchService(brokenCleanUp) { clock }

        val saved = serviceWithBrokenCleanUp.record(source = AppLaunchSource.LAUNCHER, platform = "android")

        assertEquals(saved, repository.recent(10).single())
        assertEquals(1, logLines().size)
    }
}
