package nl.vdzon.robbertsassistent.notes

import java.time.Instant
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-functietest van de opruimregel: vaste versies met vaste tijdstippen, vaste "nu".
 * Geen Firestore, geen wachttijd.
 */
class NoteVersionCleanupTest {

    private val zone = NoteVersionCleanup.ZONE

    /** 2 augustus 2026, 12:00 Amsterdamse tijd. */
    private val now: Instant = ZonedDateTime.of(2026, 8, 2, 12, 0, 0, 0, zone).toInstant()

    private fun version(id: String, month: Int, day: Int, hour: Int, minute: Int = 0) = NoteVersion(
        id = id,
        text = "tekst $id",
        savedAt = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant(),
    )

    @Test
    fun `alles binnen zeven dagen blijft staan`() {
        val versions = listOf(
            version("a", month = 8, day = 2, hour = 11),
            version("b", month = 8, day = 2, hour = 9),
            version("c", month = 8, day = 1, hour = 23),
            version("d", month = 7, day = 28, hour = 6),
            // Exact op de grens (7 dagen geleden) — blijft staan, want niet vóór de cutoff.
            NoteVersion("grens", "x", now.minus(NoteVersionCleanup.RETENTION)),
        )

        assertEquals(emptyList(), NoteVersionCleanup.idsToDelete(versions, now))
    }

    @Test
    fun `van oudere dagen blijft precies de laatste versie per kalenderdag over`() {
        val versions = listOf(
            // Binnen 7 dagen: blijft allemaal.
            version("recent-1", month = 8, day = 2, hour = 10),
            version("recent-2", month = 8, day = 1, hour = 8),
            // 20 juli: drie versies, alleen de laatste (17:00) blijft.
            version("oud-20-ochtend", month = 7, day = 20, hour = 8),
            version("oud-20-middag", month = 7, day = 20, hour = 13, minute = 30),
            version("oud-20-avond", month = 7, day = 20, hour = 17),
            // 21 juli: twee versies, alleen de laatste (22:15) blijft.
            version("oud-21-vroeg", month = 7, day = 21, hour = 7),
            version("oud-21-laat", month = 7, day = 21, hour = 22, minute = 15),
            // 22 juli: enige versie van die dag, blijft dus staan.
            version("oud-22", month = 7, day = 22, hour = 9),
        )

        val toDelete = NoteVersionCleanup.idsToDelete(versions, now).toSet()

        assertEquals(setOf("oud-20-ochtend", "oud-20-middag", "oud-21-vroeg"), toDelete)
    }

    @Test
    fun `de dag-grens wordt in Europe-Amsterdam gerekend, niet in UTC`() {
        // 20 juli 23:30 Amsterdam = 21:30 UTC; 21 juli 00:30 Amsterdam = 20 juli 22:30 UTC.
        // In UTC gerekend zouden die twee op dezelfde kalenderdag vallen en zou er één sneuvelen.
        val versions = listOf(
            version("20e-laat", month = 7, day = 20, hour = 23, minute = 30),
            version("21e-vroeg", month = 7, day = 21, hour = 0, minute = 30),
        )

        val toDelete = NoteVersionCleanup.idsToDelete(versions, now)

        assertTrue(toDelete.isEmpty(), "verwacht geen verwijderingen, kreeg $toDelete")
    }

    @Test
    fun `een lege lijst levert niets op`() {
        assertEquals(emptyList(), NoteVersionCleanup.idsToDelete(emptyList(), now))
    }
}
