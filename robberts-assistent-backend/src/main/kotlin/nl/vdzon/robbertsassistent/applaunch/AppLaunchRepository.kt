package nl.vdzon.robbertsassistent.applaunch

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

interface AppLaunchRepository {
    fun save(launch: AppLaunch): AppLaunch

    /** De laatste [limit] launches, nieuwste eerst. */
    fun recent(limit: Int): List<AppLaunch>

    /** Verwijdert alles met een `at` vóór [cutoff] en geeft het aantal verwijderde launches terug. */
    fun deleteOlderThan(cutoff: Instant): Int
}

class InMemoryAppLaunchRepository : AppLaunchRepository {
    private val launches = ConcurrentHashMap<String, AppLaunch>()

    override fun save(launch: AppLaunch): AppLaunch {
        launches[launch.id] = launch
        return launch
    }

    override fun recent(limit: Int): List<AppLaunch> =
        launches.values.sortedByDescending { it.at }.take(limit)

    override fun deleteOlderThan(cutoff: Instant): Int {
        val stale = launches.values.filter { it.at.isBefore(cutoff) }
        stale.forEach { launches.remove(it.id) }
        return stale.size
    }
}
