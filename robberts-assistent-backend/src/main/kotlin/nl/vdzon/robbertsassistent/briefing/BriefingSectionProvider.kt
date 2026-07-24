package nl.vdzon.robbertsassistent.briefing

/** Eén sectie van de dagelijkse 'Morgen'-briefing (ook de REST-DTO, zie `summary.SummaryItem`). */
data class BriefingSection(
    val key: String,
    val title: String,
    val text: String,
    // Optionele regel-items met een eventuele één-tap-actie (bv. per afspraak "reminder
    // aanmaken"). Generiek gehouden (geen agenda-specifiek type) zodat elke sectie er gebruik van
    // kan maken zonder wijziging in BriefingService/BriefingController.
    val items: List<BriefingItem> = emptyList(),
)

data class BriefingItem(
    val text: String,
    val action: BriefingAction? = null,
    // Optionele afbeelding (bv. de weerkaart-sectie): de app rendert een item met imageUrl als
    // afbeelding i.p.v. platte tekst. Bestaande secties zonder afbeelding blijven ongewijzigd.
    val imageUrl: String? = null,
    // Optionele kop boven dit item (bv. per systeemstatus-onderdeel, zie
    // `SystemStatusSectionProvider`); secties zonder heading blijven ongewijzigd.
    val heading: String? = null,
)

/**
 * Één-tap-actie bij een [BriefingItem] (bv. "reminder aanmaken"): de app doet een POST naar
 * [endpoint] met [payload] als JSON-body — geen client-kennis van de betekenis nodig.
 */
data class BriefingAction(
    val label: String,
    val endpoint: String,
    val payload: Map<String, String>,
)

/** [updatedAt] is een ISO-8601-tijdstip: wanneer deze data (gecachet of live) is opgebouwd. */
data class BriefingResponse(val sections: List<BriefingSection>, val updatedAt: String)

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
