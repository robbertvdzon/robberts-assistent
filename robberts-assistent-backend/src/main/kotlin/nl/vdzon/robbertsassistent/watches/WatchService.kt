package nl.vdzon.robbertsassistent.watches

import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.util.UUID

class WatchValidationException(message: String) : IllegalArgumentException(message)
class WatchNotFoundException(id: String) : NoSuchElementException("Zoekopdracht $id bestaat niet.")

@Service
class WatchService(private val repository: WatchRepository) {
    fun create(
        title: String,
        url: String,
        instruction: String,
        notifyOnFound: Boolean,
    ): Watch {
        val cleanTitle = title.trim()
        val cleanUrl = url.trim()
        val cleanInstruction = instruction.trim()
        validate(cleanTitle, cleanUrl, cleanInstruction)
        return repository.save(
            Watch(
                id = UUID.randomUUID().toString(),
                title = cleanTitle,
                url = cleanUrl,
                instruction = cleanInstruction,
                notifyOnFound = notifyOnFound,
            ),
        )
    }

    fun list(): List<Watch> = repository.all().sortedWith(
        compareByDescending<Watch> { it.active }.thenBy { it.title.lowercase() },
    )

    fun update(
        id: String,
        title: String,
        url: String,
        instruction: String,
        notifyOnFound: Boolean,
    ): Watch {
        val cleanTitle = title.trim()
        val cleanUrl = url.trim()
        val cleanInstruction = instruction.trim()
        validate(cleanTitle, cleanUrl, cleanInstruction)

        while (true) {
            val current = repository.findById(id) ?: throw WatchNotFoundException(id)
            val updated = current.copy(
                title = cleanTitle,
                url = cleanUrl,
                instruction = cleanInstruction,
                notifyOnFound = notifyOnFound,
                status = WatchStatus.NOG_NIET_GECONTROLEERD,
                statusDescription = "Nog niet gecontroleerd.",
                lastCheckedAt = null,
                active = true,
            )
            repository.compareAndSet(current, updated)?.let { return it }
        }
    }

    fun delete(id: String) = repository.delete(id)

    private fun validate(title: String, url: String, instruction: String) {
        if (title.isBlank()) throw WatchValidationException("Titel mag niet leeg zijn.")
        if (instruction.isBlank()) throw WatchValidationException("Zoekinstructie mag niet leeg zijn.")
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || !uri.isAbsolute || uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host.isNullOrBlank()
        ) {
            throw WatchValidationException("Webadres moet een geldige absolute HTTP(S)-URL zijn.")
        }
    }
}

interface WatchPushNotifier {
    fun found(watch: Watch)
}
