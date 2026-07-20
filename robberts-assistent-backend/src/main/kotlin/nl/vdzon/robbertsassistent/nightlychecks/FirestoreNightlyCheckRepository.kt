package nl.vdzon.robbertsassistent.nightlychecks

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Instant

/**
 * Firestore-implementatie van [NightlyCheckRepository]. Runs in de collectie `nightly-check-runs`
 * (document-id = auto-gegenereerd). Sorteert client-side (net als `FirestoreReminderRepository`) om
 * geen samengestelde index nodig te hebben voor "checkId == X, sorteer op ranAt".
 */
class FirestoreNightlyCheckRepository(private val firestore: Firestore) : NightlyCheckRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override fun save(run: CheckRun) {
        collection.document().set(run.toMap()).get()
        pruneOldRuns(run.checkId)
    }

    /** Houdt de collectie begrensd — anders groeit 'ie onbeperkt voor een check die vaak draait. */
    private fun pruneOldRuns(checkId: String) {
        val docs = collection.whereEqualTo(FIELD_CHECK_ID, checkId).get().get().documents
        if (docs.size <= MAX_HISTORY_PER_CHECK) return
        docs.sortedByDescending { it.getLong(FIELD_RAN_AT) ?: 0L }
            .drop(MAX_HISTORY_PER_CHECK)
            .forEach { it.reference.delete().get() }
    }

    override fun history(checkId: String, limit: Int): List<CheckRun> =
        collection.whereEqualTo(FIELD_CHECK_ID, checkId).get().get().documents
            .mapNotNull { it.toCheckRun() }
            .sortedByDescending { it.ranAt }
            .take(limit)

    override fun latest(checkId: String): CheckRun? = history(checkId, limit = 1).firstOrNull()

    private fun CheckRun.toMap(): Map<String, Any?> = mapOf(
        FIELD_CHECK_ID to checkId,
        FIELD_RAN_AT to ranAt.toEpochMilli(),
        FIELD_OK to result.ok,
        FIELD_SUMMARY to result.summary,
        FIELD_DETAIL to result.detail,
    )

    private fun DocumentSnapshot.toCheckRun(): CheckRun? {
        val checkId = getString(FIELD_CHECK_ID) ?: return null
        val ranAtMillis = getLong(FIELD_RAN_AT) ?: return null
        val ok = getBoolean(FIELD_OK) ?: return null
        val summary = getString(FIELD_SUMMARY) ?: return null
        return CheckRun(
            checkId = checkId,
            ranAt = Instant.ofEpochMilli(ranAtMillis),
            result = CheckResult(ok = ok, summary = summary, detail = getString(FIELD_DETAIL)),
        )
    }

    private companion object {
        const val COLLECTION = "nightly-check-runs"
        const val FIELD_CHECK_ID = "checkId"
        const val FIELD_RAN_AT = "ranAtEpochMillis"
        const val FIELD_OK = "ok"
        const val FIELD_SUMMARY = "summary"
        const val FIELD_DETAIL = "detail"
        const val MAX_HISTORY_PER_CHECK = 100
    }
}
