package nl.vdzon.robbertsassistent.nightlychecks

/**
 * Opslag van nightly-check-uitvoeringen (historie, niet alleen het laatste resultaat). Fallback is
 * [InMemoryNightlyCheckRepository]; met Firebase kiest [NightlyCheckRepositoryConfig] de
 * [FirestoreNightlyCheckRepository].
 */
interface NightlyCheckRepository {
    fun save(run: CheckRun)

    /** Meest recente uitvoeringen van [checkId], nieuwste eerst, tot maximaal [limit]. */
    fun history(checkId: String, limit: Int = 30): List<CheckRun>

    /** Meest recente uitvoering van [checkId], of `null` als de check nog nooit gedraaid heeft. */
    fun latest(checkId: String): CheckRun?
}
