package nl.vdzon.robbertsassistent.waste

import java.time.LocalDate

/** Eén afvalophaalmoment (bv. "gft & etensresten" op een datum). */
data class WastePickup(
    val type: String,
    val date: LocalDate,
)

/**
 * Resultaat van een afvalkalender-ophaal-poging. Bij een netwerk-/serverfout is [pickups] leeg
 * en [error] gezet — de aanroeper (`WasteTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class WasteSchedule(
    val pickups: List<WastePickup>,
    val error: String? = null,
)

/**
 * Afvalophaalkalender voor Robberts huisadres (Heemskerk, ingezameld door HVC). Fase 0 (keyless,
 * geen secret nodig — postcode/huisnummer zijn config, geen geheim, zie CLAUDE.md §5):
 * [HvcWasteClient] is de enige, altijd-actieve implementatie. [StubWasteClient] bestaat alleen
 * voor tests, zodat tools zonder netwerk-call getest kunnen worden.
 */
interface WasteClient {
    /** Aankomende ophaalmomenten, oplopend op datum. */
    fun upcomingPickups(): WasteSchedule
}
