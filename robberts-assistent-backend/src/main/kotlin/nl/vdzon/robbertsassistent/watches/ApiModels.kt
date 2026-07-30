package nl.vdzon.robbertsassistent.watches

data class WatchResponse(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: String,
    val notifyOnFound: Boolean,
    val status: String,
    val statusText: String,
    val active: Boolean,
    // ISO-8601 tijdstip (UTC), of null als de watch nog niet gecontroleerd is.
    val lastCheckedAt: String?,
)

data class WatchesResponse(val watches: List<WatchResponse>)

data class CreateWatchRequest(
    val title: String = "",
    val url: String = "",
    val instruction: String = "",
    val frequency: String = "",
    val notifyOnFound: Boolean = true,
)

data class UpdateWatchRequest(
    val title: String = "",
    val url: String = "",
    val instruction: String = "",
    val frequency: String = "",
    val notifyOnFound: Boolean = true,
)

fun Watch.toResponse() = WatchResponse(
    id = id,
    title = title,
    url = url,
    instruction = instruction,
    frequency = frequency.name,
    notifyOnFound = notifyOnFound,
    status = status.name,
    statusText = statusText,
    active = active,
    lastCheckedAt = lastCheckedAt?.toString(),
)
