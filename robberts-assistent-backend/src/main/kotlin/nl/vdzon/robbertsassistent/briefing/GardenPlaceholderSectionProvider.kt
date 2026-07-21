package nl.vdzon.robbertsassistent.briefing

import org.springframework.stereotype.Component

/**
 * Moestuin-placeholder: dummy-regel, zelfde stijl als de bestaande hardcoded moestuin-regel in
 * `summary.SummaryService` — wordt later vervangen door een echte bron.
 */
@Component
class GardenPlaceholderSectionProvider : BriefingSectionProvider {
    override val order = 30

    override fun section() = BriefingSection(
        key = "moestuin",
        title = "Moestuin",
        text = "Niets dringends — water geven kan wachten tot morgen.",
    )
}
