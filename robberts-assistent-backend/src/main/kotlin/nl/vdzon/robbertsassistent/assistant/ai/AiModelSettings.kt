package nl.vdzon.robbertsassistent.assistant.ai

import com.google.cloud.firestore.Firestore
import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.config.AppSecrets
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** De drie toepassingen waarvoor los een AI-model te kiezen is. */
enum class AiRole(val label: String, val description: String) {
    CHAT("Assistent", "De chat en de praatmodus, inclusief het gebruik van tools."),
    NUTRITION("Eetdagboek", "Foto's van eten beoordelen en het eet-geheugen bijwerken."),
    BACKGROUND("Overig", "Briefing, zoekopdrachten, moestuin-chat, gesprekstitels en het geheugen van de assistent."),
}

/** Opslag van de modelkeuze per toepassing. Zonder Firebase in het geheugen van de pod. */
interface AiModelRepository {
    fun find(role: AiRole): RuntimeExecution?
    fun save(role: AiRole, execution: RuntimeExecution)
    fun delete(role: AiRole)
}

class InMemoryAiModelRepository : AiModelRepository {
    private val store = ConcurrentHashMap<AiRole, RuntimeExecution>()
    override fun find(role: AiRole): RuntimeExecution? = store[role]
    override fun save(role: AiRole, execution: RuntimeExecution) {
        store[role] = execution
    }

    override fun delete(role: AiRole) {
        store.remove(role)
    }
}

/** Eén document per toepassing in de collectie `ai-models` (document-id = de rol). */
class FirestoreAiModelRepository(private val firestore: Firestore) : AiModelRepository {
    private val collection get() = firestore.collection("ai-models")

    override fun find(role: AiRole): RuntimeExecution? {
        val snapshot = collection.document(role.name).get().get()
        if (!snapshot.exists()) return null
        val vendor = snapshot.getString("vendorId").orEmpty()
        val model = snapshot.getString("model").orEmpty()
        val mode = snapshot.getString("mode").orEmpty()
        return if (vendor.isBlank() || model.isBlank() || mode.isBlank()) null else RuntimeExecution(vendor, model, mode)
    }

    override fun save(role: AiRole, execution: RuntimeExecution) {
        collection.document(role.name)
            .set(mapOf("vendorId" to execution.vendorId, "model" to execution.model, "mode" to execution.mode)).get()
    }

    override fun delete(role: AiRole) {
        collection.document(role.name).delete().get()
    }
}

@Configuration
class AiModelStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun aiModelRepository(firebase: FirebaseProvider): AiModelRepository {
        if (!firebase.isConfigured) {
            logger.info("AI-modelkeuze: in-memory (geen Firebase-config)")
            return InMemoryAiModelRepository()
        }
        return runCatching { FirestoreAiModelRepository(firebase.firestore()) }
            .onSuccess { logger.info("AI-modelkeuze: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory AI-modelkeuze", it)
                InMemoryAiModelRepository()
            }
    }
}

/**
 * Welk model elke toepassing gebruikt. De keuze uit het beheerscherm staat in de opslag en geldt
 * direct, zonder uitrol; zonder keuze geldt de configuratie (`RA_AI_VENDOR`/`_MODEL`/`_MODE`).
 * De opslag wordt kort gecachet: de chat doet per beurt meerdere runtime-jobs.
 */
@Service
class AiModelSettings(private val repository: AiModelRepository, private val secrets: AppSecrets) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<AiRole, Pair<Long, RuntimeExecution?>>()

    val default: RuntimeExecution get() = RuntimeExecution(secrets.aiVendor, secrets.aiModel, secrets.aiMode)

    /** De eigen keuze voor [role], of `null` als die er niet is (dan geldt [default]). */
    fun chosen(role: AiRole): RuntimeExecution? {
        val now = System.currentTimeMillis()
        cache[role]?.takeIf { now - it.first < CACHE_MILLIS }?.let { return it.second }
        // Een storing in de opslag mag de AI niet platleggen: dan geldt het standaardmodel.
        val stored = runCatching { repository.find(role) }
            .onFailure { logger.warn("AI-modelkeuze voor {} lezen mislukt: {}", role, it.message) }
            .getOrNull()
        cache[role] = now to stored
        return stored
    }

    fun execution(role: AiRole): RuntimeExecution = chosen(role) ?: default

    fun update(role: AiRole, execution: RuntimeExecution) {
        repository.save(role, execution)
        cache.remove(role)
    }

    fun reset(role: AiRole) {
        repository.delete(role)
        cache.remove(role)
    }

    private companion object {
        const val CACHE_MILLIS = 30_000L
    }
}

data class AiExecutionDto(val vendorId: String = "", val model: String = "", val mode: String = "")

data class AiRoleDto(
    val role: String,
    val label: String,
    val description: String,
    val execution: AiExecutionDto,
    /** `true` als dit een eigen keuze is; `false` als het standaardmodel geldt. */
    val custom: Boolean,
)

data class AiOptionDto(val execution: AiExecutionDto, val available: Boolean, val onlineWorkers: Int)

data class AiModelsDto(
    val roles: List<AiRoleDto>,
    val default: AiExecutionDto,
    val options: List<AiOptionDto>,
    /** Gevuld als de modellenlijst niet bij de runtime op te halen was. */
    val optionsError: String?,
)

/** Beheer van de modelkeuze: de drie toepassingen, hun huidige model en de modellen van de runtime. */
@RestController
class AiModelsController(
    private val authService: AuthService,
    private val settings: AiModelSettings,
    private val gateway: AgentRuntimeGateway,
) {
    @GetMapping("/api/v1/ai-models")
    fun get(@RequestHeader("Authorization", required = false) authorization: String?): AiModelsDto {
        authService.requireAuthorization(authorization)
        return overview()
    }

    @PutMapping("/api/v1/ai-models/{role}")
    fun update(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable role: String,
        @RequestBody request: AiExecutionDto,
    ): AiModelsDto {
        authService.requireAuthorization(authorization)
        if (request.vendorId.isBlank() || request.model.isBlank() || request.mode.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Leverancier, model en modus zijn alle drie nodig")
        }
        settings.update(parse(role), RuntimeExecution(request.vendorId.trim(), request.model.trim(), request.mode.trim()))
        return overview()
    }

    /** Terug naar het standaardmodel uit de configuratie. */
    @DeleteMapping("/api/v1/ai-models/{role}")
    fun reset(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable role: String,
    ): AiModelsDto {
        authService.requireAuthorization(authorization)
        settings.reset(parse(role))
        return overview()
    }

    private fun parse(role: String): AiRole =
        runCatching { AiRole.valueOf(role.uppercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende toepassing") }

    private fun overview(): AiModelsDto {
        val options = runCatching { gateway.executionOptions() }
        return AiModelsDto(
            roles = AiRole.entries.map { role ->
                AiRoleDto(role.name, role.label, role.description, settings.execution(role).toDto(), settings.chosen(role) != null)
            },
            default = settings.default.toDto(),
            options = options.getOrDefault(emptyList()).map { AiOptionDto(it.execution.toDto(), it.available, it.onlineWorkers) },
            optionsError = options.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName },
        )
    }

    private fun RuntimeExecution.toDto() = AiExecutionDto(vendorId, model, mode)
}
