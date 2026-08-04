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

/**
 * Verifieert dat de scheduler over álle documenten ophaalt → selecteert → verwijdert en het totaal
 * op INFO logt.
 */
class NoteVersionCleanupSchedulerTest {

    private val zone = NoteVersionCleanup.ZONE
    private val now: Instant = ZonedDateTime.of(2026, 8, 2, 3, 30, 0, 0, zone).toInstant()
    private val repository = InMemoryNotesRepository()
    private val notesService = NotesService(repository) { now }

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

    private fun addVersion(documentId: String, text: String, month: Int, day: Int, hour: Int) =
        repository.addVersion(
            documentId,
            text,
            ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, zone).toInstant(),
        )

    @Test
    fun `verwijdert alleen de oude dubbelingen en logt het aantal`() {
        val recent = addVersion(DEFAULT_DOCUMENT_ID, "vandaag", month = 8, day = 2, hour = 1)
        val oudOchtend = addVersion(DEFAULT_DOCUMENT_ID, "oud ochtend", month = 7, day = 20, hour = 8)
        val oudAvond = addVersion(DEFAULT_DOCUMENT_ID, "oud avond", month = 7, day = 20, hour = 20)

        NoteVersionCleanupScheduler(notesService, repository) { now }.cleanup()

        assertEquals(
            setOf(recent.id, oudAvond.id),
            repository.allVersions(DEFAULT_DOCUMENT_ID).map { it.id }.toSet(),
        )
        assertEquals(null, repository.version(DEFAULT_DOCUMENT_ID, oudOchtend.id))

        val logRegels = appender.list.filter { it.level == Level.INFO }
        assertEquals(1, logRegels.size)
        assertEquals("Notitie-versies opgeruimd: 1 verwijderd", logRegels.single().formattedMessage)
    }

    @Test
    fun `ruimt in elk document op en logt het totaal in één regel`() {
        val recepten = notesService.createDocument("recepten")
        addVersion(DEFAULT_DOCUMENT_ID, "todo oud ochtend", month = 7, day = 20, hour = 8)
        val todoBewaard = addVersion(DEFAULT_DOCUMENT_ID, "todo oud avond", month = 7, day = 20, hour = 20)
        addVersion(recepten.id, "recept oud ochtend", month = 7, day = 21, hour = 8)
        val receptBewaard = addVersion(recepten.id, "recept oud avond", month = 7, day = 21, hour = 20)

        NoteVersionCleanupScheduler(notesService, repository) { now }.cleanup()

        assertEquals(listOf(todoBewaard.id), repository.allVersions(DEFAULT_DOCUMENT_ID).map { it.id })
        assertEquals(listOf(receptBewaard.id), repository.allVersions(recepten.id).map { it.id })

        val logRegels = appender.list.filter { it.level == Level.INFO }
        assertEquals(1, logRegels.size)
        assertEquals("Notitie-versies opgeruimd: 2 verwijderd", logRegels.single().formattedMessage)
    }

    @Test
    fun `een falende repository laat de job niet crashen`() {
        val broken = object : NotesRepository by repository {
            override fun allVersions(documentId: String): List<NoteVersion> =
                throw IllegalStateException("Firestore down")
        }

        NoteVersionCleanupScheduler(notesService, broken) { now }.cleanup()

        assertEquals(1, appender.list.count { it.level == Level.WARN })
    }
}
