package nl.vdzon.robbertsassistent.watches

import java.time.Instant

/**
 * Langdurige zoekopdracht die periodiek een webpagina controleert op een bepaalde conditie.
 * Bij een transitie naar [WatchStatus.GEVONDEN] stuurt de scheduler een push en zet [active] op false.
 */
data class Watch(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: WatchFrequency,
    val status: WatchStatus = WatchStatus.ONBEKEND,
    val statusText: String? = null,
    val lastChecked: Instant? = null,
    val active: Boolean = true,
)

enum class WatchStatus {
    ONBEKEND,
    GEVONDEN,
    NIET_GEVONDEN,
}

enum class WatchFrequency {
    /** Ma-vr 09:00-17:00, maximaal één check per uur. */
    KANTOORUREN,
    /** Maximaal één check per 24 uur. */
    DAGELIJKS,
}
