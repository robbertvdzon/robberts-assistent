package nl.vdzon.robbertsassistent.watches

import java.time.Instant

/**
 * Frequentie waarmee een watch gecheckt moet worden.
 * - KANTOORUREN: ma-vr 09:00-17:00, elk uur
 * - DAGELIJKS: eenmaal per dag (rond middernacht)
 */
enum class WatchFrequency {
    KANTOORUREN,
    DAGELIJKS,
}

/**
 * Status van een watch:
 * - ONBEKEND: nog niet gecheckt of AI niet beschikbaar
 * - GEVONDEN: het gezochte item is gevonden
 * - NIET_GEVONDEN: het gezochte item is (nog) niet gevonden
 */
enum class WatchStatus {
    ONBEKEND,
    GEVONDEN,
    NIET_GEVONDEN,
}

/**
 * Een langdurige zoekopdracht: de backend haalt periodiek de [url] op en laat een AI beoordelen
 * of aan de [instruction] is voldaan. Bij een transitie naar [WatchStatus.GEVONDEN] volgt een
 * pushmelding en wordt de watch automatisch inactief ([active] = false).
 */
data class Watch(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: WatchFrequency,
    val status: WatchStatus = WatchStatus.ONBEKEND,
    val active: Boolean = true,
    val lastChecked: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
