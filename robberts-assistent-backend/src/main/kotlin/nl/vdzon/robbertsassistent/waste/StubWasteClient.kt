package nl.vdzon.robbertsassistent.waste

import java.time.LocalDate

/**
 * Vaste, deterministische ophaalmomenten — puur voor tests, zodat `WasteTools` zonder
 * netwerk-call getest kan worden (zelfde patroon als `StubCalendarClient`). Niet als Spring-bean
 * geregistreerd: [HvcWasteClient] is keyless en dus altijd actief.
 */
class StubWasteClient : WasteClient {
    override fun upcomingPickups(): WasteSchedule {
        val today = LocalDate.now()
        return WasteSchedule(
            listOf(
                WastePickup("gft & etensresten", today.plusDays(2)),
                WastePickup("plastic, blik & drinkpakken", today.plusDays(5)),
            ),
        )
    }
}
