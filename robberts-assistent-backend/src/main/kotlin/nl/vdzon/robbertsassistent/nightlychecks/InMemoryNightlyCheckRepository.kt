package nl.vdzon.robbertsassistent.nightlychecks

import java.util.concurrent.ConcurrentHashMap

/** In-memory fallback: per check-id de laatste [MAX_HISTORY_PER_CHECK] runs, nieuwste eerst. */
class InMemoryNightlyCheckRepository : NightlyCheckRepository {
    private val runsByCheck = ConcurrentHashMap<String, MutableList<CheckRun>>()

    @Synchronized
    override fun save(run: CheckRun) {
        val runs = runsByCheck.getOrPut(run.checkId) { mutableListOf() }
        runs.add(0, run)
        while (runs.size > MAX_HISTORY_PER_CHECK) runs.removeAt(runs.size - 1)
    }

    override fun history(checkId: String, limit: Int): List<CheckRun> =
        runsByCheck[checkId]?.take(limit) ?: emptyList()

    override fun latest(checkId: String): CheckRun? = runsByCheck[checkId]?.firstOrNull()

    private companion object {
        const val MAX_HISTORY_PER_CHECK = 100
    }
}
