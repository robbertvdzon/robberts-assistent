package nl.vdzon.robbertsassistent.openshift

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Europe/Amsterdam zit in juli op CEST (UTC+2) — de verwachte kloktijden hieronder zijn UTC + 2u. */
class TimeMachineDescribeTest {

    private val now = Instant.parse("2026-07-24T12:00:00Z")

    @Test
    fun `beschrijft een bekende eigenaar met grootte en relatieve ouderdom`() {
        val backup = TimeMachineBackup(name = "Robbert's MacBook Pro", sizeGb = 620.0, lastModified = now.minus(Duration.ofHours(3)))

        val result = backup.describe(now)

        assertEquals("Robbert: 620.0 GB, laatst geschreven 24 jul 11:00 (3 uur geleden)", result)
    }

    @Test
    fun `valt terug op de ruwe sparsebundle-naam voor een onbekende eigenaar`() {
        val backup = TimeMachineBackup(name = "Onbekende Mac", sizeGb = 100.0, lastModified = now)

        val result = backup.describe(now)

        assertEquals("Onbekende Mac: 100.0 GB, laatst geschreven 24 jul 14:00 (minder dan een uur geleden)", result)
    }

    @Test
    fun `toont een foutmelding voor een mislukte bundel`() {
        val backup = TimeMachineBackup(name = "MacBook Pro", error = "Permission denied")

        val result = backup.describe(now)

        assertEquals("Karen: onbekend (Permission denied)", result)
    }

    @Test
    fun `status combineert meerdere backups met puntkomma`() {
        val status = TimeMachineStatus(
            backups = listOf(
                TimeMachineBackup(name = "Robbert's MacBook Pro", sizeGb = 620.0, lastModified = now.minus(Duration.ofDays(3))),
                TimeMachineBackup(name = "MacBook Pro", sizeGb = 410.0, lastModified = now.minus(Duration.ofHours(1))),
            ),
        )

        val result = status.describe(now)

        assertEquals(
            "Robbert: 620.0 GB, laatst geschreven 21 jul 14:00 (3 dagen geleden); " +
                "Karen: 410.0 GB, laatst geschreven 24 jul 13:00 (1 uur geleden)",
            result,
        )
    }

    @Test
    fun `status toont een foutmelding als het hele pad niet uit te lezen was`() {
        val status = TimeMachineStatus(error = "geen mount gevonden")

        assertEquals("onbekend (geen mount gevonden)", status.describe(now))
    }

    @Test
    fun `status meldt een lege lijst`() {
        assertEquals("geen backups gevonden", TimeMachineStatus().describe(now))
    }
}
