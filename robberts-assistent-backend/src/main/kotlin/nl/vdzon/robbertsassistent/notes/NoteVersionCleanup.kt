package nl.vdzon.robbertsassistent.notes

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Pure selectielogica voor het nachtelijke opruimen van notitie-versies: geen Firestore, geen
 * klok, dus zonder wachttijd te testen.
 *
 * Regel: alles wat binnen [RETENTION] van `now` is opgeslagen blijft volledig staan. Van alles
 * daarvóór blijft per kalenderdag (Europe/Amsterdam) alleen de laatst opgeslagen versie over.
 */
object NoteVersionCleanup {

    val RETENTION: Duration = Duration.ofDays(7)
    val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")

    /** De ids van de versies die weg mogen; de volgorde van [versions] doet er niet toe. */
    fun idsToDelete(versions: List<NoteVersion>, now: Instant): List<String> {
        val cutoff = now.minus(RETENTION)
        return versions
            .filter { it.savedAt.isBefore(cutoff) }
            .groupBy { it.savedAt.atZone(ZONE).toLocalDate() }
            .flatMap { (_, sameDay) ->
                // Nieuwste van die dag houden; bij een exact gelijk tijdstip beslist het id, zodat
                // de uitkomst deterministisch is.
                val keep = sameDay.maxWithOrNull(compareBy({ it.savedAt }, { it.id }))
                sameDay.filter { it.id != keep?.id }
            }
            .map { it.id }
    }
}
