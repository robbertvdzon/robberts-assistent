package nl.vdzon.robbertsassistent.zonneplan

/**
 * Vaste, deterministische zonnepanelen-status — puur voor tests (zelfde patroon als
 * `StubAutomowerClient`). [ZonneplanClientConfig] kiest deze zodra `RA_HOME_ASSISTENT_URL`/
 * `_TOKEN` ontbreken.
 */
class StubZonneplanClient : ZonneplanClient {
    override fun status(): SolarStatusResult = SolarStatusResult(currentPowerWatt = 850, yesterdayYieldKwh = 12.4)
}
