package nl.vdzon.robbertsassistent.assistant

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Status van een asynchrone chat-beurt. */
enum class ChatJobStatus { RUNNING, DONE, FAILED }

/** Momentopname van een chat-job; [result] is gevuld bij [ChatJobStatus.DONE], [error] bij FAILED. */
data class ChatJob(
    val id: String,
    val status: ChatJobStatus,
    val createdAt: Instant,
    val result: ChatResult? = null,
    val error: String? = null,
)

/**
 * Draait chat-beurten op de achtergrond, zodat een request niet hoeft te wachten tot de AI klaar is.
 * De Agent Runtime doet er per ronde ~20-60 s over; een tool-ronde kost er elk nog een, en
 * Cloudflare kapt requests na ~100 s af. De app krijgt daarom direct een job-id terug en haalt het
 * antwoord daarna op via [get].
 *
 * Jobstatus staat in het geheugen van deze pod: na een herstart zijn lopende jobs weg (de app krijgt
 * dan een 404 en meldt een fout). Afgeronde jobs worden na [RETENTION] opgeruimd.
 */
@Component
class AssistantChatJobs(private val assistantService: AssistantService) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val jobs = ConcurrentHashMap<String, ChatJob>()
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "assistant-chat-job").apply { isDaemon = true }
    }

    /** Start de beurt op de achtergrond en geeft meteen het job-id terug. */
    fun submit(conversationId: String?, text: String, uploads: List<PhotoUpload>, voice: Boolean): String {
        cleanup()
        val id = UUID.randomUUID().toString()
        jobs[id] = ChatJob(id = id, status = ChatJobStatus.RUNNING, createdAt = Instant.now())
        executor.submit {
            val started = jobs.getValue(id)
            jobs[id] = try {
                started.copy(status = ChatJobStatus.DONE, result = assistantService.chat(conversationId, text, uploads, voice))
            } catch (e: Throwable) {
                logger.warn("Chat-job {} mislukt", id, e)
                started.copy(status = ChatJobStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
            }
        }
        return id
    }

    fun get(id: String): ChatJob? = jobs[id]

    private fun cleanup() {
        val limit = Instant.now().minus(RETENTION)
        jobs.entries.removeIf { it.value.status != ChatJobStatus.RUNNING && it.value.createdAt.isBefore(limit) }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private companion object {
        val RETENTION: Duration = Duration.ofHours(1)
    }
}
