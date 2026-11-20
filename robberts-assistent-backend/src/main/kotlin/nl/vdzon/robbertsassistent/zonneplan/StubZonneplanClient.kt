package nl.vdzon.robbertsassistent.zonneplan

/**
 * Vaste, deterministische zonnepanelen-status — puur voor tests (zelfde patroon als
 * `StubAutomowerClient`). [ZonneplanClientConfig] kiest deze zodra `RA_HOME_ASSISTENT_URL`/
 * `_TOKEN` ontbreken.
 */
class StubZonneplanClient : ZonneplanClient {
    override fun status(): SolarStatusResult = SolarStatusResult(currentPowerWatt = 850, yesterdayYieldKwh = 12.4)

    /** Zestig dagen met een vast, golvend patroon, zodat de grafiek lokaal iets te tonen heeft. */
    override fun dailyYields(since: java.time.LocalDate): Map<java.time.LocalDate, Double> {
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Amsterdam"))
        return (0L..59L).map { today.minusDays(it) }.filter { !it.isBefore(since) }
            .associateWith { 6.0 + 5.0 * kotlin.math.sin(it.toEpochDay() / 3.0) }
    }
}
