package nl.vdzon.robbertsassistent.zonneplan

/**
 * Resultaat van een zonnepanelen-statusopvraag. [currentPowerWatt] is puur informatief (huidig
 * omvormervermogen); [yesterdayYieldKwh] is de dagopbrengst van gisteren — nagenoeg 0 op een dag
 * zonder volledige verduistering wijst op een storing (zie [nl.vdzon.robbertsassistent.zonneplan.ZonneplanCouplingProbe]).
 * Bij een netwerk-/serverfout zijn beide velden `null` en is [error] gezet.
 */
data class SolarStatusResult(
    val currentPowerWatt: Int?,
    val yesterdayYieldKwh: Double?,
    val error: String? = null,
)

/**
 * Zonnepanelen-koppeling via de Zonneplan-integratie in Home Assistant (eigen thuis-cluster,
 * `home-assistant.apps.sno.lab.vdzon.com`) — geen los Zonneplan-account/API nodig. Actief zodra
 * `RA_HOME_ASSISTENT_URL` + `RA_HOME_ASSISTENT_TOKEN` gezet zijn (zie
 * [nl.vdzon.robbertsassistent.config.AppSecrets]); anders [StubZonneplanClient].
 */
interface ZonneplanClient {
    /** Huidig omvormervermogen (W) + dagopbrengst van gisteren (kWh). */
    fun status(): SolarStatusResult
}
