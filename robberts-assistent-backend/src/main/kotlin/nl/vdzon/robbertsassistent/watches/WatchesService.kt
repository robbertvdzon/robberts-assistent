package nl.vdzon.robbertsassistent.watches

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Business-logica voor watches: CRUD + status-updates.
 */
@Service
class WatchesService(private val repository: WatchRepository) {

    fun create(title: String, url: String, instruction: String, frequency: WatchFrequency): Watch {
        val now = Instant.now()
        val watch = Watch(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            url = url.trim(),
            instruction = instruction.trim(),
            frequency = frequency,
            createdAt = now,
            updatedAt = now,
        )
        return repository.save(watch)
    }

    fun all(): List<Watch> = repository.all()

    fun findById(id: String): Watch? = repository.findById(id)

    fun activeWatches(): List<Watch> = repository.activeWatches()

    fun toggle(id: String): Watch? {
        val watch = repository.findById(id) ?: return null
        val updated = watch.copy(
            active = !watch.active,
            updatedAt = Instant.now(),
        )
        return repository.save(updated)
    }

    fun delete(id: String) {
        repository.delete(id)
    }

    fun updateStatus(watch: Watch, status: WatchStatus, deactivate: Boolean = false): Watch {
        val updated = watch.copy(
            status = status,
            active = if (deactivate) false else watch.active,
            lastChecked = Instant.now(),
            updatedAt = Instant.now(),
        )
        return repository.save(updated)
    }

    fun markChecked(watch: Watch): Watch {
        val updated = watch.copy(
            lastChecked = Instant.now(),
            updatedAt = Instant.now(),
        )
        return repository.save(updated)
    }
}
