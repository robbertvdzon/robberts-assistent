package nl.vdzon.robbertsassistent.summary

import org.springframework.stereotype.Service

/**
 * Levert de dagelijkse samenvatting. Nu nog hardcoded dummy-data; wordt later per item
 * vervangen door een echte bron (windmeter-API, moestuin-checklist, backup-status, OpenShift-
 * health, zonnepanelen-monitoring).
 */
@Service
class SummaryService {
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
            SummaryItem(
                key = "openshift",
                title = "OpenShift",
                text = "Cluster is gezond, alle deployments draaien.",
            ),
            SummaryItem(
                key = "zonnepanelen",
                title = "Zonnepanelen",
                text = "Alle panelen produceren normaal.",
            ),
        ),
    )
}
