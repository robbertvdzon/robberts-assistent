package nl.vdzon.robbertsassistent.couplings

/**
 * Status van één externe koppeling voor het "Koppelingen"-scherm in de app.
 *
 * @param configured of de bijbehorende secret(s) gezet zijn.
 * @param mode `"echt"` (de echte koppeling is actief) of `"fallback"` (stub/in-memory/log).
 * @param test resultaat van de live-test; `null` in de lijst-weergave (die doet geen netwerk-calls).
 */
data class CouplingStatus(
    val id: String,
    val name: String,
    val description: String,
    val configured: Boolean,
    val mode: String,
    val test: TestResult? = null,
)

/** Uitkomst van een live-test tegen de externe dienst. */
data class TestResult(
    val ok: Boolean,
    val detail: String,
    val durationMs: Long,
)
