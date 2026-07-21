package nl.vdzon.robbertsassistent.briefing

/** Eén sectie van de dagelijkse 'Morgen'-briefing (ook de REST-DTO, zie `summary.SummaryItem`). */
data class BriefingSection(
    val key: String,
    val title: String,
    val text: String,
)

data class BriefingResponse(val sections: List<BriefingSection>)

/**
 * SPI voor precies één briefingsectie (bv. kite/strandfiets, agenda, weektaken, moestuin). Elke
 * module die zo'n sectie aanbiedt, registreert een `@Component` die dit implementeert;
 * [BriefingService] pikt alle implementaties automatisch op via Spring's
 * `List<BriefingSectionProvider>`-injectie — zelfde SPI-patroon als `couplings.CouplingProbe` en
 * `nightlychecks.NightlyCheck`. Een nieuwe sectie toevoegen (zie story 2 van 2: systeem-
 * checkrapport) betekent dus alleen een nieuwe `BriefingSectionProvider`-implementatie, geen
 * wijziging in [BriefingService].
 */
interface BriefingSectionProvider {
    /** Bepaalt de volgorde van secties in de briefing (oplopend). */
    val order: Int

    /** Bouwt de sectie op. Mag netwerk-/AI-calls doen; gooit geen exception (zie [BriefingService]). */
    fun section(): BriefingSection

    /**
     * Korte, kernachtige samenvatting van deze sectie voor de 18:00-push (bv. "kiten 🟢 avond
     * 24kn"), of `null` om deze sectie in de pushtekst over te slaan (zie [BriefingScheduler]).
     * Standaard `null` — alleen relevant voor secties die zich lenen voor een one-liner.
     */
    fun shortSummary(): String? = null
}
