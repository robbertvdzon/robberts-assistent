package nl.vdzon.robbertsassistent.watches

import java.time.Instant

/** Hoe vaak een [Watch] gecontroleerd wordt (zie `WatchScheduler.isDue`). */
enum class WatchFrequency {
    /** Elk uur, ma t/m vr tussen 09:00 en 17:00 (Europe/Amsterdam). */
    KANTOORUREN,

    /** Eén keer per kalenderdag (Europe/Amsterdam). */
    DAGELIJKS,
    ;

    companion object {
        /** Tolerante conversie vanuit de API/Firestore; `null` bij een onbekende waarde. */
        fun fromName(name: String?): WatchFrequency? =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

/**
 * Eén langlopende zoekopdracht: houd [url] in de gaten en meld het zodra [instruction] van
 * toepassing is (bv. "meld het als de aaltjes weer op voorraad zijn").
 *
 * [lastStatus] is de laatste korte Nederlandse statuszin van de AI-beoordeling, [found] of die
 * beoordeling "gevonden" opleverde, en [lastError] een eventuele fout van de laatste poging
 * (netwerk/HTTP/AI) — bij een fout blijft de vorige status staan.
 */
data class Watch(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: WatchFrequency = WatchFrequency.DAGELIJKS,
    val pushOnFound: Boolean = true,
    val active: Boolean = true,
    val lastCheckedAt: Instant? = null,
    val lastStatus: String? = null,
    val found: Boolean = false,
    val lastError: String? = null,
    val createdAt: Instant = Instant.EPOCH,
)
