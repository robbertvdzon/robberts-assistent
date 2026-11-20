package nl.vdzon.robbertsassistent.jobs

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import jakarta.annotation.PreDestroy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import nl.vdzon.robbertsassistent.push.PushService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

enum class LeadVerdict { NIEUW, INTERESSANT, NIET }

/** Eén gevonden vacature, met Robberts oordeel en de reden die hij erbij gaf. */
data class JobLead(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val url: String,
    val summary: String,
    val foundAt: Instant,
    val verdict: LeadVerdict = LeadVerdict.NIEUW,
    val reason: String = "",
)

/**
 * Wat er gezocht wordt en wat er uit de oordelen geleerd is. [preferences] is vrije tekst die de AI
 * bijhoudt; [blockedCompanies] wordt daarnaast in code afgedwongen, zodat een afgewezen bedrijf
 * gegarandeerd wegblijft.
 */
data class JobSearchSettings(
    val description: String = DEFAULT_DESCRIPTION,
    val preferences: String = "",
    val blockedCompanies: List<String> = emptyList(),
    val lastRunAt: Instant? = null,
    val lastRunNote: String = "Nog niet gezocht.",
) {
    companion object {
        const val DEFAULT_DESCRIPTION =
            "Vacatures voor een ervaren Java/Kotlin software engineer of een AI-specialist (AI engineer, " +
                "LLM/agent-ontwikkeling), bij werkgevers binnen ongeveer 30 minuten rijden van Heemskerk " +
                "(bijvoorbeeld Beverwijk, IJmuiden, Haarlem, Alkmaar, Zaandam, Amsterdam-West/Sloterdijk). " +
                "Grotendeels thuiswerken met een kantoor in dat gebied telt ook."
    }
}

interface JobRepository {
    fun settings(): JobSearchSettings
    fun saveSettings(settings: JobSearchSettings)
    fun leads(): List<JobLead>
    fun findLead(id: String): JobLead?
    fun saveLead(lead: JobLead): JobLead
}

class InMemoryJobRepository : JobRepository {
    @Volatile
    private var settings = JobSearchSettings()
    private val leads = ConcurrentHashMap<String, JobLead>()

    override fun settings() = settings
    override fun saveSettings(settings: JobSearchSettings) {
        this.settings = settings
    }

    override fun leads() = leads.values.toList()
    override fun findLead(id: String) = leads[id]
    override fun saveLead(lead: JobLead) = lead.also { leads[it.id] = it }
}

/** `job-search/settings` en één document per vacature in `job-leads`. */
class FirestoreJobRepository(private val firestore: Firestore) : JobRepository {
    private val settingsDocument get() = firestore.collection("job-search").document("settings")
    private val leadCollection get() = firestore.collection("job-leads")

    override fun settings(): JobSearchSettings {
        val s = settingsDocument.get().get()
        if (!s.exists()) return JobSearchSettings()
        return JobSearchSettings(
            description = s.getString("description")?.takeIf { it.isNotBlank() } ?: JobSearchSettings.DEFAULT_DESCRIPTION,
            preferences = s.getString("preferences").orEmpty(),
            blockedCompanies = (s.get("blockedCompanies") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            lastRunAt = s.getLong("lastRunAt")?.let(Instant::ofEpochMilli),
            lastRunNote = s.getString("lastRunNote").orEmpty(),
        )
    }

    override fun saveSettings(settings: JobSearchSettings) {
        settingsDocument.set(
            mapOf(
                "description" to settings.description,
                "preferences" to settings.preferences,
                "blockedCompanies" to settings.blockedCompanies,
                "lastRunAt" to settings.lastRunAt?.toEpochMilli(),
                "lastRunNote" to settings.lastRunNote,
            ),
        ).get()
    }

    override fun leads(): List<JobLead> = leadCollection.get().get().documents.map { it.toLead() }

    override fun findLead(id: String): JobLead? = leadCollection.document(id).get().get().takeIf { it.exists() }?.toLead()

    override fun saveLead(lead: JobLead): JobLead {
        leadCollection.document(lead.id).set(
            mapOf(
                "title" to lead.title,
                "company" to lead.company,
                "location" to lead.location,
                "url" to lead.url,
                "summary" to lead.summary,
                "foundAt" to lead.foundAt.toEpochMilli(),
                "verdict" to lead.verdict.name,
                "reason" to lead.reason,
            ),
        ).get()
        return lead
    }

    private fun DocumentSnapshot.toLead() = JobLead(
        id = id,
        title = getString("title").orEmpty(),
        company = getString("company").orEmpty(),
        location = getString("location").orEmpty(),
        url = getString("url").orEmpty(),
        summary = getString("summary").orEmpty(),
        foundAt = Instant.ofEpochMilli(getLong("foundAt") ?: 0L),
        verdict = runCatching { LeadVerdict.valueOf(getString("verdict").orEmpty()) }.getOrDefault(LeadVerdict.NIEUW),
        reason = getString("reason").orEmpty(),
    )
}

@Configuration
class JobStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jobRepository(firebase: FirebaseProvider): JobRepository {
        if (!firebase.isConfigured) {
            logger.info("Vacatures: in-memory (geen Firebase-config)")
            return InMemoryJobRepository()
        }
        return runCatching { FirestoreJobRepository(firebase.firestore()) }
            .onSuccess { logger.info("Vacatures: Firestore") }
            .getOrElse {
                logger.error("Firestore-init (vacatures) faalde, val terug op in-memory", it)
                InMemoryJobRepository()
            }
    }
}

data class FoundJob(val title: String, val company: String, val location: String, val url: String, val summary: String)

/** Wat er uit één oordeel geleerd is: de bijgewerkte voorkeurentekst en de bedrijven die wegblijven. */
data class LearnedPreferences(val preferences: String, val blockedCompanies: List<String>)

/** De AI-kant: vacatures zoeken op het web, en leren van een oordeel. */
interface JobFinder {
    fun find(settings: JobSearchSettings, known: List<JobLead>): List<FoundJob>
    fun learn(settings: JobSearchSettings, lead: JobLead): LearnedPreferences?
}

class LeadNotFoundException(id: String) : RuntimeException("Vacature $id niet gevonden")

/**
 * De zoekopdracht voor werk: elke ochtend laat hij de AI naar vacatures zoeken, bewaart wat nieuw
 * is en stuurt een push. Robbert geeft per vacature aan of die interessant is, met een reden;
 * daarvan leert de volgende ronde. Een afgewezen bedrijf blijft daarna gegarandeerd weg.
 */
@Service
class JobSearchService(
    private val repository: JobRepository,
    private val finder: JobFinder,
    private val push: PushService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "job-search").apply { isDaemon = true } }
    private val running = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()

    fun settings(): JobSearchSettings = repository.settings()

    /** Nieuwste eerst. */
    fun leads(): List<JobLead> = repository.leads().sortedByDescending { it.foundAt }

    @Scheduled(cron = "0 30 8 * * *", zone = "Europe/Amsterdam")
    fun scheduled() = start(notify = true)

    /** De allereerste keer meteen zoeken, zodat het scherm niet tot morgenochtend leeg blijft. */
    @EventListener(ApplicationReadyEvent::class)
    fun firstRun() {
        runCatching { if (repository.settings().lastRunAt == null) start(notify = true) }
            .onFailure { logger.warn("Eerste vacaturezoekronde niet gestart: {}", it.message) }
    }

    /** Start een zoekronde op de achtergrond; `false` als er al een loopt. */
    fun start(notify: Boolean): Boolean {
        if (!running.compareAndSet(false, true)) return false
        executor.submit {
            try {
                run(notify)
            } finally {
                running.set(false)
            }
        }
        return true
    }

    internal fun run(notify: Boolean): List<JobLead> {
        val settings = repository.settings()
        val known = repository.leads()
        val found = runCatching { finder.find(settings, known) }.getOrElse {
            logger.warn("Vacatures zoeken mislukt: {}", it.message)
            repository.saveSettings(repository.settings().copy(lastRunAt = Instant.now(), lastRunNote = "Zoeken is mislukt; morgen opnieuw."))
            return emptyList()
        }
        val fresh = filterNew(found, known, settings.blockedCompanies).map {
            repository.saveLead(JobLead(UUID.randomUUID().toString(), it.title, it.company, it.location, it.url, it.summary, Instant.now()))
        }
        val note = when (fresh.size) {
            0 -> "Niets nieuws gevonden."
            1 -> "1 nieuwe vacature gevonden."
            else -> "${fresh.size} nieuwe vacatures gevonden."
        }
        repository.saveSettings(repository.settings().copy(lastRunAt = Instant.now(), lastRunNote = note))
        logger.info("Vacatures: {} gemeld door de AI, {} nieuw", found.size, fresh.size)
        if (notify && fresh.isNotEmpty()) {
            runCatching {
                push.sendToAll(
                    if (fresh.size == 1) "Nieuwe vacature" else "${fresh.size} nieuwe vacatures",
                    fresh.take(3).joinToString("; ") { "${it.title} bij ${it.company}" },
                    mapOf("type" to "jobs"),
                )
            }.onFailure { logger.warn("Push over vacatures mislukt: {}", it.message) }
        }
        return fresh
    }

    /** Het oordeel van Robbert, met reden. Daarna worden de voorkeuren bijgewerkt. */
    fun rate(id: String, verdict: LeadVerdict, reason: String): JobLead {
        require(verdict != LeadVerdict.NIEUW) { "Kies interessant of niet interessant" }
        val lead = repository.findLead(id) ?: throw LeadNotFoundException(id)
        val rated = repository.saveLead(lead.copy(verdict = verdict, reason = reason.trim().take(MAX_REASON)))
        val settings = repository.settings()
        val learned = runCatching { finder.learn(settings, rated) }
            .onFailure { logger.warn("Leren van het oordeel over {} mislukt: {}", rated.company, it.message) }
            .getOrNull() ?: fallbackLearning(settings, rated)
        repository.saveSettings(
            repository.settings().copy(
                preferences = learned.preferences,
                blockedCompanies = learned.blockedCompanies.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            ),
        )
        return rated
    }

    fun updateSettings(description: String, preferences: String, blockedCompanies: List<String>): JobSearchSettings {
        require(description.isNotBlank()) { "De zoekvraag mag niet leeg zijn" }
        val updated = repository.settings().copy(
            description = description.trim(),
            preferences = preferences.trim(),
            blockedCompanies = blockedCompanies.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
        )
        repository.saveSettings(updated)
        return updated
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    internal companion object {
        const val MAX_REASON = 500
        const val MAX_PER_RUN = 10

        /** Wat de AI meldt, zonder wat al bekend is en zonder afgewezen bedrijven. */
        internal fun filterNew(found: List<FoundJob>, known: List<JobLead>, blocked: List<String>): List<FoundJob> {
            val seenUrls = known.map { normalizeUrl(it.url) }.toMutableSet()
            val seenTitles = known.map { titleKey(it.title, it.company) }.toMutableSet()
            return found.filter { job ->
                if (isBlocked(job.company, blocked)) return@filter false
                // `add` geeft false als hij er al in zat; zo vallen ook dubbelen binnen één ronde weg.
                val newUrl = seenUrls.add(normalizeUrl(job.url))
                val newTitle = seenTitles.add(titleKey(job.title, job.company))
                newUrl && newTitle
            }.take(MAX_PER_RUN)
        }

        internal fun isBlocked(company: String, blocked: List<String>): Boolean {
            val name = company.lowercase().trim()
            if (name.isEmpty()) return false
            return blocked.any { b ->
                val block = b.lowercase().trim()
                block.isNotEmpty() && (name.contains(block) || block.contains(name))
            }
        }

        private fun normalizeUrl(url: String): String =
            url.lowercase().substringBefore('?').substringBefore('#').removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").trimEnd('/')

        private fun titleKey(title: String, company: String): String =
            "${title.lowercase().filter { it.isLetterOrDigit() }}|${company.lowercase().filter { it.isLetterOrDigit() }}"

        /**
         * Zonder AI (mislukt of proefmodus): het oordeel als regel onderaan de voorkeuren, en het
         * bedrijf op de lijst als de reden het bedrijf bij naam noemt.
         */
        internal fun fallbackLearning(settings: JobSearchSettings, lead: JobLead): LearnedPreferences {
            val word = if (lead.verdict == LeadVerdict.INTERESSANT) "Interessant" else "Niet interessant"
            val line = "- $word: ${lead.title} bij ${lead.company}" + if (lead.reason.isBlank()) "" else " (${lead.reason})"
            val mentionsCompany = lead.company.isNotBlank() && lead.company.split(' ').filter { it.length > 2 }
                .any { lead.reason.contains(it, ignoreCase = true) }
            val blocked = when {
                lead.verdict == LeadVerdict.INTERESSANT -> settings.blockedCompanies.filterNot { isBlocked(lead.company, listOf(it)) }
                mentionsCompany -> settings.blockedCompanies + lead.company
                else -> settings.blockedCompanies
            }
            return LearnedPreferences(listOf(settings.preferences, line).filter { it.isNotBlank() }.joinToString("\n"), blocked)
        }
    }
}

data class JobLeadDto(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val url: String,
    val summary: String,
    val foundAt: String,
    val verdict: String,
    val reason: String,
)

data class JobOverviewDto(
    val description: String,
    val preferences: String,
    val blockedCompanies: List<String>,
    val lastRunAt: String?,
    val lastRunNote: String,
    val running: Boolean,
    val leads: List<JobLeadDto>,
)

data class RateLeadRequest(val verdict: String = "", val reason: String = "")
data class JobSettingsRequest(val description: String = "", val preferences: String = "", val blockedCompanies: List<String> = emptyList())

@RestController
class JobSearchController(private val authService: AuthService, private val service: JobSearchService) {

    @GetMapping("/api/v1/jobs")
    fun overview(@RequestHeader("Authorization", required = false) authorization: String?): JobOverviewDto {
        authService.requireAuthorization(authorization)
        return dto()
    }

    /** Start een zoekronde op de achtergrond; het overzicht meldt `running` tot hij klaar is. */
    @PostMapping("/api/v1/jobs/run")
    fun run(@RequestHeader("Authorization", required = false) authorization: String?): JobOverviewDto {
        authService.requireAuthorization(authorization)
        service.start(notify = false)
        return dto()
    }

    @PutMapping("/api/v1/jobs/{id}/verdict")
    fun rate(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: RateLeadRequest,
    ): JobOverviewDto {
        authService.requireAuthorization(authorization)
        val verdict = runCatching { LeadVerdict.valueOf(request.verdict.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Onbekend oordeel") }
        service.rate(id, verdict, request.reason)
        return dto()
    }

    @PutMapping("/api/v1/jobs/settings")
    fun settings(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: JobSettingsRequest,
    ): JobOverviewDto {
        authService.requireAuthorization(authorization)
        service.updateSettings(request.description, request.preferences, request.blockedCompanies)
        return dto()
    }

    @ExceptionHandler(LeadNotFoundException::class)
    fun notFound(e: LeadNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to (e.message ?: "Niet gevonden")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to (e.message ?: "Ongeldig verzoek")))

    private fun dto(): JobOverviewDto {
        val settings = service.settings()
        return JobOverviewDto(
            description = settings.description,
            preferences = settings.preferences,
            blockedCompanies = settings.blockedCompanies,
            lastRunAt = settings.lastRunAt?.toString(),
            lastRunNote = settings.lastRunNote,
            running = service.isRunning,
            leads = service.leads().map {
                JobLeadDto(it.id, it.title, it.company, it.location, it.url, it.summary, it.foundAt.toString(), it.verdict.name, it.reason)
            },
        )
    }
}
