package nl.vdzon.robbertsassistent.automower

/** Status van één Husqvarna Automower-robotmaaier. */
data class MowerStatus(
    val name: String,
    val model: String,
    val mode: String,
    val activity: String,
    val state: String,
    val batteryPercent: Int?,
    val errorCode: Int,
    val connected: Boolean,
)

/**
 * Resultaat van een status-ophaal-poging. Bij een netwerk-/serverfout is [mowers] leeg en
 * [error] gezet — de aanroeper (`AutomowerTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class MowerStatusResult(
    val mowers: List<MowerStatus>,
    val error: String? = null,
)

/** Resultaat van een actie (starten/parkeren). [ok] is false bij een fout, met [error] als uitleg. */
data class MowerActionResult(
    val ok: Boolean,
    val error: String? = null,
)

/**
 * Robotmaaier-koppeling (Husqvarna Automower Connect API, client_credentials — een geregistreerde
 * app-key/secret, zie developer.husqvarnagroup.cloud). Actief zodra `RA_HUSQVARNA_APP_KEY` +
 * `_SECRET` gezet zijn (zie [nl.vdzon.robbertsassistent.config.AppSecrets]); anders
 * [StubAutomowerClient]. Werkt op de eerste (enige) maaier van het account.
 */
interface AutomowerClient {
    /** Status van alle maaiers op het account (in de praktijk: één). */
    fun status(): MowerStatusResult

    /** Start de maaier voor [durationMinutes] minuten, los van het normale schema. */
    fun startMowing(durationMinutes: Int): MowerActionResult

    /** Stuurt de maaier terug naar het laadstation; hervat vanzelf het normale schema. */
    fun park(): MowerActionResult
}

/** Nederlandse omschrijving van de Husqvarna-`mower.activity`-waarde. */
internal fun activityDescription(activity: String): String = when (activity) {
    "MOWING" -> "maait"
    "GOING_HOME" -> "gaat naar laadstation"
    "CHARGING" -> "laadt op"
    "LEAVING" -> "vertrekt"
    "PARKED_IN_CS" -> "geparkeerd in laadstation"
    "STOPPED_IN_GARDEN" -> "gestopt in de tuin"
    "NOT_APPLICABLE" -> "n.v.t."
    "UNKNOWN" -> "onbekend"
    else -> activity
}

/** Nederlandse omschrijving van de Husqvarna-`mower.state`-waarde. */
internal fun stateDescription(state: String): String = when (state) {
    "IN_OPERATION" -> "actief"
    "PAUSED" -> "gepauzeerd"
    "RESTRICTED" -> "beperkt door schema"
    "OFF" -> "uit"
    "STOPPED" -> "gestopt"
    "ERROR" -> "fout"
    "FATAL_ERROR" -> "kritieke fout"
    "WAIT_UPDATING" -> "bezig met update"
    "WAIT_POWER_UP" -> "start op"
    "ERROR_AT_POWER_UP" -> "fout bij opstarten"
    "NOT_APPLICABLE" -> "n.v.t."
    else -> state
}
