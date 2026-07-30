package nl.vdzon.robbertsassistent.watches

import java.time.Instant

/** Hoe vaak een [Watch] gecontroleerd moet worden; de "aan de beurt?"-logica zit in [WatchScheduling]. */
enum class WatchFrequency {
    /** Ma–vr, 09:00–17:00, elk uur. */
    KANTOORUREN,

    /** Eén keer per (kalender)dag. */
    DAGELIJKS,
}

/** Resultaat van de laatste AI-beoordeling van een [Watch]. */
enum class WatchStatus {
    /** Nog niet (succesvol) gecontroleerd, of een onverwacht/leeg AI-antwoord. */
    ONBEKEND,
    NIET_GEVONDEN,
    GEVONDEN,
}

/**
 * Eén langlopende zoekopdracht: Robbert wil periodiek weten of aan [instruction] is voldaan op de
 * pagina op [url] (bv. "waarschuw me zodra dit product weer op voorraad is"). [status]/[statusText]
 * zijn het resultaat van de laatste beoordeling; [active] = false zodra de watch gestopt is met
 * pollen (na een transitie naar [WatchStatus.GEVONDEN], zie [WatchScheduler]).
 */
data class Watch(
    val id: String,
    val title: String,
    val url: String,
    val instruction: String,
    val frequency: WatchFrequency,
    val notifyOnFound: Boolean,
    val status: WatchStatus = WatchStatus.ONBEKEND,
    val statusText: String = "",
    val active: Boolean = true,
    val lastCheckedAt: Instant? = null,
)
