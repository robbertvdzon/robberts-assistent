package nl.vdzon.robbertsassistent.summary

import nl.vdzon.robbertsassistent.nightlychecks.NightlyCheckStatus
import nl.vdzon.robbertsassistent.nightlychecks.NightlyChecksService
import org.springframework.stereotype.Service

/**
 * Levert de dagelijkse samenvatting. De "wind"/"moestuin"/"backups"/"zonnepanelen"-items zijn nog
 * hardcoded dummy-data (worden later per stuk vervangen door een echte bron); de nightly-check-
 * resultaten (zie [NightlyChecksService], bv. OpenShift-gezondheid) zijn al echt en verschijnen
 * hier automatisch zodra er een nieuwe [nl.vdzon.robbertsassistent.nightlychecks.NightlyCheck]
 * bijkomt — geen wijziging hier nodig.
 */
@Service
class SummaryService(private val nightlyChecksService: NightlyChecksService) {
    fun current(): SummaryResponse = SummaryResponse(
        items = listOf(
            SummaryItem(
                key = "wind",
                title = "Wind",
                text = "18 knopen, uit het zuidwesten.",
            ),
            SummaryItem(
                key = "moestuin",
                title = "Moestuin",
                text = "Niets dringends — water geven kan wachten tot morgen.",
            ),
            SummaryItem(
                key = "backups",
                title = "Backups",
                text = "Alle laptop-backups zijn gezond, laatste run vannacht geslaagd.",
            ),
        ) + nightlyChecksService.list().map { it.toSummaryItem() } + listOf(
            SummaryItem(
                key = "zonnepanelen",
                title = "Zonnepanelen",
                text = "Alle panelen produceren normaal.",
            ),
        ),
    )

    private fun NightlyCheckStatus.toSummaryItem(): SummaryItem {
        val text = lastRun?.let { run -> if (run.result.ok) run.result.summary else "⚠️ ${run.result.summary}" }
            ?: "Nog niet gedraaid."
        return SummaryItem(key = id, title = name, text = text)
    }
}
