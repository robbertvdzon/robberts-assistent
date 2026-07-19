package nl.vdzon.robbertsassistent.automower

/**
 * Vaste, deterministische maaierstatus — puur voor tests, zodat `AutomowerTools` zonder
 * netwerk-call getest kan worden (zelfde patroon als `StubCalendarClient`). [AutomowerClientConfig]
 * kiest deze zodra `RA_HUSQVARNA_APP_KEY`/`_SECRET` ontbreken.
 */
class StubAutomowerClient : AutomowerClient {
    override fun status(): MowerStatusResult = MowerStatusResult(
        listOf(
            MowerStatus(
                name = "Testmaaier",
                model = "Husqvarna Automower 310E NERA",
                mode = "MAIN_AREA",
                activity = "MOWING",
                state = "IN_OPERATION",
                batteryPercent = 78,
                errorCode = 0,
                connected = true,
            ),
        ),
    )

    override fun startMowing(durationMinutes: Int): MowerActionResult = MowerActionResult(ok = true)

    override fun park(): MowerActionResult = MowerActionResult(ok = true)
}
