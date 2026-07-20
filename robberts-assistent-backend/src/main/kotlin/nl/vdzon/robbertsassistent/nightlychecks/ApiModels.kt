package nl.vdzon.robbertsassistent.nightlychecks

data class CheckRunResponse(
    // ISO-8601 tijdstip (UTC).
    val ranAt: String,
    val ok: Boolean,
    val summary: String,
    val detail: String?,
)

data class NightlyCheckStatusResponse(
    val id: String,
    val name: String,
    val description: String,
    val cronSchedule: String,
    val lastRun: CheckRunResponse?,
)

data class NightlyChecksResponse(val checks: List<NightlyCheckStatusResponse>)
data class CheckRunHistoryResponse(val runs: List<CheckRunResponse>)

fun CheckRun.toResponse() = CheckRunResponse(
    ranAt = ranAt.toString(),
    ok = result.ok,
    summary = result.summary,
    detail = result.detail,
)

fun NightlyCheckStatus.toResponse() = NightlyCheckStatusResponse(
    id = id,
    name = name,
    description = description,
    cronSchedule = cronSchedule,
    lastRun = lastRun?.toResponse(),
)
