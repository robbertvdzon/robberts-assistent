package nl.vdzon.robbertsassistent.watches

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchesResponse(val watches: List<WatchResponse>)

data class WatchResponse(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: String,
    val status: String,
    val statusText: String?,
    val lastChecked: String?,
    val active: Boolean,
)

data class CreateWatchRequest(
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: String,
)

private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Europe/Amsterdam"))

fun Watch.toResponse() = WatchResponse(
    id = id,
    title = title,
    url = url,
    instruction = instruction,
    frequency = frequency.name,
    status = status.name,
    statusText = statusText,
    lastChecked = lastChecked?.let { isoFormatter.format(it) },
    active = active,
)
