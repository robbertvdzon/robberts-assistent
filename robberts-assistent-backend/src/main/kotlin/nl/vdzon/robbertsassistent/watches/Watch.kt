package nl.vdzon.robbertsassistent.watches

import java.time.Instant

enum class WatchStatus {
    NOG_NIET_GECONTROLEERD,
    NIET_GEVONDEN,
    GEVONDEN,
    ONBEKEND,
}

data class Watch(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val notifyOnFound: Boolean,
    val status: WatchStatus = WatchStatus.NOG_NIET_GECONTROLEERD,
    val statusDescription: String = "Nog niet gecontroleerd.",
    val lastCheckedAt: Instant? = null,
    val active: Boolean = true,
)
