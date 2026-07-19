package nl.vdzon.robbertsassistent.couplings

/**
 * SPI voor precies één externe koppeling. Elke module die een koppeling aanbiedt, registreert een
 * `@Component` die dit implementeert; [CouplingsService] pikt alle implementaties automatisch op
 * via Spring's `List<CouplingProbe>`-injectie. Een nieuwe koppeling toevoegen betekent dus alleen
 * een nieuwe `CouplingProbe`-implementatie in de eigen module — geen wijziging in
 * `CouplingsService` of de app nodig, en die verschijnt vanzelf op het "Koppelingen"-scherm (dat
 * volledig data-driven is vanuit `GET /api/v1/couplings`).
 */
interface CouplingProbe {
    val id: String
    val name: String
    val description: String

    /** Of de koppeling geconfigureerd is (secret gezet), zonder netwerk-call. Keyless => altijd true. */
    val configured: Boolean

    /** `"echt"` als de echte implementatie actief is, `"fallback"` als het de stub/in-memory/log is. */
    val mode: String

    /** Lichte, niet-destructieve live-test; mag een netwerk-call doen. */
    fun test(): Pair<Boolean, String>
}
