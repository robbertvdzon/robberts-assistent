package nl.vdzon.robbertsassistent.watches

data class WatchResponse(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: String,
    val pushOnFound: Boolean,
    val active: Boolean,
    // ISO-8601 tijdstippen (UTC), `null` zolang er nog niet gecontroleerd is.
    val lastCheckedAt: String?,
    val lastStatus: String?,
    val found: Boolean,
    val lastError: String?,
    val createdAt: String,
)

data class WatchesResponse(val watches: List<WatchResponse>)

/** Body van zowel `POST /api/v1/watches` als `PUT /api/v1/watches/{id}`. */
data class SaveWatchRequest(
    val title: String = "",
    val url: String = "",
    val instruction: String = "",
    val frequency: String = WatchFrequency.DAGELIJKS.name,
    val pushOnFound: Boolean = true,
    // Alleen relevant bij een update: hiermee pauzeer/hervat je een zoekopdracht.
    val active: Boolean = true,
)

fun Watch.toResponse() = WatchResponse(
    id = id,
    title = title,
    url = url,
    instruction = instruction,
    frequency = frequency.name,
    pushOnFound = pushOnFound,
    active = active,
    lastCheckedAt = lastCheckedAt?.toString(),
    lastStatus = lastStatus,
    found = found,
    lastError = lastError,
    createdAt = createdAt.toString(),
)
