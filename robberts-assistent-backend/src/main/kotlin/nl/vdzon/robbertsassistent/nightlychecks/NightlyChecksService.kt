package nl.vdzon.robbertsassistent.nightlychecks

import org.springframework.stereotype.Service

/** Statusoverzicht van één check — voor het "Nachtchecks"-scherm in de app. */
data class NightlyCheckStatus(
    val id: String,
    val name: String,
    val description: String,
    val cronSchedule: String,
    val lastRun: CheckRun?,
)

/**
 * Leest de status van alle geregistreerde [NightlyCheck]s en laat er handmatig eentje opnieuw
 * draaien (voor de "herstart deze check"-knop in de app).
 */
@Service
class NightlyChecksService(
    private val checks: List<NightlyCheck>,
    private val repository: NightlyCheckRepository,
    private val scheduler: NightlyCheckScheduler,
) {
    fun list(): List<NightlyCheckStatus> = checks.map { check ->
        NightlyCheckStatus(
            id = check.id,
            name = check.name,
            description = check.description,
            cronSchedule = check.cronSchedule,
            lastRun = repository.latest(check.id),
        )
    }

    /** `null` als [checkId] niet bestaat. */
    fun history(checkId: String, limit: Int): List<CheckRun>? {
        if (checks.none { it.id == checkId }) return null
        return repository.history(checkId, limit)
    }

    /** Draait de check meteen (buiten zijn schema om) en geeft het nieuwe resultaat terug; `null` als [checkId] niet bestaat. */
    fun runNow(checkId: String): CheckRun? {
        val check = checks.firstOrNull { it.id == checkId } ?: return null
        scheduler.runAndStore(check)
        return repository.latest(checkId)
    }
}
