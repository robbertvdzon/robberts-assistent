package nl.vdzon.robbertsassistent.watches

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Beheert zoekopdrachten (watches): aanmaken, opsommen, bewerken, verwijderen, en (voor
 * [WatchScheduler]) de actieve watches opvragen + na een beoordeling opslaan.
 */
@Service
class WatchesService(private val repository: WatchRepository) {

    fun create(
        title: String,
        url: String,
        instruction: String,
        frequency: WatchFrequency,
        notifyOnFound: Boolean,
    ): Watch = repository.save(
        Watch(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
            notifyOnFound = notifyOnFound,
        ),
    )

    /** Alle watches, alfabetisch op titel. */
    fun list(): List<Watch> = repository.all().sortedBy { it.title.lowercase() }

    /**
     * Bewerkt een watch. Een bewerking telt als "opnieuw aanpassen" (zie de story): de watch wordt
     * weer actief en de vorige beoordeling wordt gewist, zodat de eerstvolgende poll met een schone
     * lei opnieuw beoordeelt.
     */
    fun update(
        id: String,
        title: String,
        url: String,
        instruction: String,
        frequency: WatchFrequency,
        notifyOnFound: Boolean,
    ): Watch {
        val existing = repository.findById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Watch niet gevonden")
        return repository.save(
            existing.copy(
                title = title,
                url = url,
                instruction = instruction,
                frequency = frequency,
                notifyOnFound = notifyOnFound,
                active = true,
                status = WatchStatus.ONBEKEND,
                statusText = "",
                lastCheckedAt = null,
            ),
        )
    }

    fun delete(id: String) = repository.delete(id)

    /** Actieve watches, voor [WatchScheduler]. */
    fun active(): List<Watch> = repository.all().filter { it.active }

    fun save(watch: Watch): Watch = repository.save(watch)
}
