package nl.vdzon.robbertsassistent.watches

import java.time.Instant

enum class WatchStatus {
    NOG_NIET_GECONTROLEERD,
    NIET_GEVONDEN,
    GEVONDEN,
    ONBEKEND,
}

data class Watch(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val notifyOnFound: Boolean,
    val status: WatchStatus = WatchStatus.NOG_NIET_GECONTROLEERD,
    val statusDescription: String = "Nog niet gecontroleerd.",
    val lastCheckedAt: Instant? = null,
    val active: Boolean = true,
    /** Sinds wanneer de opdracht (opnieuw) zoekt; gezet bij aanmaken, wijzigen of de eerste controle. */
    val searchingSince: Instant? = null,
    /** Wanneer de voorwaarde waar werd. Zonder (oude vondsten) komt er geen dagelijkse herinnering. */
    val foundAt: Instant? = null,
    /** Wanneer Robbert de vondst als gezien heeft bevestigd; tot dan volgt elke dag een herinnering. */
    val acknowledgedAt: Instant? = null,
    val lastRemindedAt: Instant? = null,
    /** Sinds wanneer de controle achter elkaar mislukt (pagina weg, AI onbereikbaar). */
    val failingSince: Instant? = null,
    /** Wat de AI verder opviel op de pagina, bijvoorbeeld dat het product uit het assortiment lijkt. */
    val note: String = "",
)
