package nl.vdzon.robbertsassistent.watches

import org.springframework.stereotype.Service
import java.util.UUID

/**
 * CRUD-operaties voor watches.
 */
@Service
class WatchesService(private val repository: WatchRepository) {

    fun list(): List<Watch> = repository.all()

    fun create(title: String, url: String, instruction: String, frequency: WatchFrequency): Watch {
        val watch = Watch(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            instruction = instruction,
            frequency = frequency,
        )
        return repository.save(watch)
    }

    fun delete(id: String) = repository.delete(id)

    fun save(watch: Watch): Watch = repository.save(watch)

    fun findById(id: String): Watch? = repository.findById(id)
}
