package nl.vdzon.robbertsassistent.notes

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifieert dat de scheduler ophaalt → selecteert → verwijdert en het aantal op INFO logt. */
class NoteVersionCleanupSchedulerTest {

    private val zone = NoteVersionCleanup.ZONE
    private val now: Instant = ZonedDateTime.of(2026, 8, 2, 3, 30, 0, 0, zone).toInstant()
    private val repository = InMemoryNotesRepository()

    private val logger = LoggerFactory.getLogger(NoteVersionCleanupScheduler::class.java) as Logger
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

    private fun addVersion(text: String, month: Int, day: Int, hour: Int) =
        repository.addVersion(text, ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant())

    @Test
    fun `verwijdert alleen de oude dubbelingen en logt het aantal`() {
        val recent = addVersion("vandaag", month = 8, day = 2, hour = 1)
        val oudOchtend = addVersion("oud ochtend", month = 7, day = 20, hour = 8)
        val oudAvond = addVersion("oud avond", month = 7, day = 20, hour = 20)

        NoteVersionCleanupScheduler(repository) { now }.cleanup()

        assertEquals(
            setOf(recent.id, oudAvond.id),
            repository.allVersions().map { it.id }.toSet(),
        )
        assertEquals(null, repository.version(oudOchtend.id))

        val logRegels = appender.list.filter { it.level == Level.INFO }
        assertEquals(1, logRegels.size)
        assertEquals("Notitie-versies opgeruimd: 1 verwijderd", logRegels.single().formattedMessage)
    }

    @Test
    fun `een falende repository laat de job niet crashen`() {
        val broken = object : NotesRepository by repository {
            override fun allVersions(): List<NoteVersion> = throw IllegalStateException("Firestore down")
        }

        NoteVersionCleanupScheduler(broken) { now }.cleanup()

        assertEquals(1, appender.list.count { it.level == Level.WARN })
    }
}
