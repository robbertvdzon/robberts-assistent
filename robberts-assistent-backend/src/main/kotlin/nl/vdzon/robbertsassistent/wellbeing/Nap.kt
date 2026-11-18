package nl.vdzon.robbertsassistent.wellbeing

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.firebase.FirebaseProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Een dutje overdag. Robbert start het zelf ("ik ga nu slapen") of vult het achteraf in. Zolang
 * [end] ontbreekt loopt het nog. Meldt hij zich niet af, dan schat de app het eind uit de eerste
 * tekenen dat hij weer wakker was; [endEstimated] zegt dat het eind zo'n schatting is.
 */
data class Nap(
    val id: String,
    val start: Instant,
    val end: Instant? = null,
    val endEstimated: Boolean = false,
    /** De wekker die bij het starten is gezet, als die er was. De wekker zelf loopt op de telefoon. */
    val alarmAt: Instant? = null,
    val createdAt: Instant = start,
) {
    val open: Boolean get() = end == null
}

interface NapRepository {
    fun save(nap: Nap): Nap
    fun findById(id: String): Nap?

    /** Dutjes met `from <= start < to`, oplopend in tijd. */
    fun findBetween(from: Instant, to: Instant): List<Nap>
    fun delete(id: String)
}

class InMemoryNapRepository : NapRepository {
    private val store = ConcurrentHashMap<String, Nap>()

    override fun save(nap: Nap): Nap = nap.also { store[it.id] = it }
    override fun findById(id: String): Nap? = store[id]
    override fun findBetween(from: Instant, to: Instant): List<Nap> =
        store.values.filter { !it.start.isBefore(from) && it.start.isBefore(to) }.sortedBy { it.start }

    override fun delete(id: String) {
        store.remove(id)
    }
}

/** Eén document per dutje in de collectie `naps`; tijden als epoch-millis. */
class FirestoreNapRepository(private val firestore: Firestore) : NapRepository {
    private val collection get() = firestore.collection("naps")

    override fun save(nap: Nap): Nap {
        collection.document(nap.id).set(nap.toMap()).get()
        return nap
    }

    override fun findById(id: String): Nap? = collection.document(id).get().get().takeIf { it.exists() }?.toNap()

    override fun findBetween(from: Instant, to: Instant): List<Nap> =
        collection
            .whereGreaterThanOrEqualTo(FIELD_START, from.toEpochMilli())
            .whereLessThan(FIELD_START, to.toEpochMilli())
            .get().get().documents
            .mapNotNull { it.toNap() }
            .sortedBy { it.start }

    override fun delete(id: String) {
        collection.document(id).delete().get()
    }

    private fun Nap.toMap(): Map<String, Any> = buildMap {
        put(FIELD_START, start.toEpochMilli())
        end?.let { put("endEpochMillis", it.toEpochMilli()) }
        put("endEstimated", endEstimated)
        alarmAt?.let { put("alarmAtEpochMillis", it.toEpochMilli()) }
        put("createdAtEpochMillis", createdAt.toEpochMilli())
    }

    private fun DocumentSnapshot.toNap(): Nap? {
        val start = getLong(FIELD_START) ?: return null
        return Nap(
            id = id,
            start = Instant.ofEpochMilli(start),
            end = getLong("endEpochMillis")?.let(Instant::ofEpochMilli),
            endEstimated = getBoolean("endEstimated") ?: false,
            alarmAt = getLong("alarmAtEpochMillis")?.let(Instant::ofEpochMilli),
            createdAt = Instant.ofEpochMilli(getLong("createdAtEpochMillis") ?: start),
        )
    }

    companion object {
        private const val FIELD_START = "startEpochMillis"
    }
}

@Configuration
class NapStoreConfig {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun napRepository(firebase: FirebaseProvider): NapRepository {
        if (!firebase.isConfigured) {
            logger.info("Dutjes: in-memory (geen Firebase-config)")
            return InMemoryNapRepository()
        }
        return runCatching { FirestoreNapRepository(firebase.firestore()) }
            .onSuccess { logger.info("Dutjes: Firestore") }
            .getOrElse {
                logger.error("Firestore-init faalde, val terug op in-memory dutjes", it)
                InMemoryNapRepository()
            }
    }
}

class NapNotFoundException : RuntimeException("Dutje niet gevonden")
class NapConflictException(message: String) : RuntimeException(message)

@Service
class NapService(
    private val repository: NapRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Legt een dutje vast. Zonder [end] begint er een lopend dutje (hooguit één tegelijk); met
     * [end] is het een dutje dat achteraf wordt ingevuld.
     */
    fun create(start: Instant? = null, end: Instant? = null, alarmAt: Instant? = null): Nap {
        val now = clock.instant()
        val from = start ?: now
        require(!from.isAfter(now.plus(FUTURE_SLACK))) { "Een dutje kan niet in de toekomst beginnen" }
        validate(from, end)
        if (end == null) {
            require(alarmAt == null || alarmAt.isAfter(from)) { "De wekker moet na het begin van het dutje staan" }
            if (open() != null) throw NapConflictException("Er loopt al een dutje")
        }
        return repository.save(
            Nap(id = UUID.randomUUID().toString(), start = from, end = end, alarmAt = alarmAt.takeIf { end == null }, createdAt = now),
        )
    }

    /**
     * Past begin en/of eind aan. Een opgegeven eind is altijd van Robbert zelf en vervangt dus een
     * schatting. [alarmAt] zet een (nieuwe) wekkertijd, [clearAlarm] haalt hem weg.
     */
    fun update(id: String, start: Instant? = null, end: Instant? = null, alarmAt: Instant? = null, clearAlarm: Boolean = false): Nap {
        val nap = repository.findById(id) ?: throw NapNotFoundException()
        val newStart = start ?: nap.start
        val newEnd = end ?: nap.end
        validate(newStart, newEnd)
        return repository.save(
            nap.copy(
                start = newStart,
                end = newEnd,
                endEstimated = if (end != null) false else nap.endEstimated,
                alarmAt = if (clearAlarm) null else alarmAt ?: nap.alarmAt,
            ),
        )
    }

    /**
     * Verwerkt een schatting van het eind. [evidence] is het vroegste teken dat Robbert weer wakker
     * was (eerste stappen, een foto, een check-in, de wekker), of `null` als er niets is. Een eind
     * dat hij zelf opgaf blijft altijd staan; een eerdere schatting wordt alleen vervangen door een
     * vroeger teken. Ligt het teken te ver weg om iets te zeggen, of is er na [GIVE_UP_AFTER] nog
     * niets, dan wordt het de wekkertijd of anders [DEFAULT_DURATION].
     */
    fun applyEstimate(id: String, evidence: Instant?): Nap {
        val nap = repository.findById(id) ?: throw NapNotFoundException()
        if (!nap.open && !nap.endEstimated) return nap
        val now = clock.instant()
        val usable = evidence?.takeIf { it.isAfter(nap.start) && !it.isAfter(nap.start.plus(MAX_ESTIMATED)) }
        val fallback = (nap.alarmAt?.takeIf { it.isAfter(nap.start) && !it.isAfter(nap.start.plus(MAX_ESTIMATED)) }
            ?: nap.start.plus(DEFAULT_DURATION))
        val estimate = when {
            usable != null -> usable
            evidence != null -> fallback
            Duration.between(nap.start, now) >= GIVE_UP_AFTER -> fallback
            else -> null
        } ?: return nap
        val current = nap.end
        if (current != null && !estimate.isBefore(current)) return nap
        return repository.save(nap.copy(end = estimate, endEstimated = true))
    }

    fun get(id: String): Nap = repository.findById(id) ?: throw NapNotFoundException()

    /** Het dutje dat nu loopt, als dat er is. Een dutje van meer dan een dag oud telt niet meer als lopend. */
    fun open(): Nap? {
        val now = clock.instant()
        return repository.findBetween(now.minus(Duration.ofDays(1)), now.plus(FUTURE_SLACK)).lastOrNull { it.open }
    }

    /** Dutjes van de laatste dagen waarvan het eind nog ontbreekt of geschat is. */
    fun pending(): List<Nap> {
        val now = clock.instant()
        return repository.findBetween(now.minus(PENDING_WINDOW), now.plus(FUTURE_SLACK)).filter { it.open || it.endEstimated }
    }

    /** Dutjes die op [date] (Europe/Amsterdam) begonnen. */
    fun day(date: LocalDate): List<Nap> = repository.findBetween(
        date.atStartOfDay(CheckInService.ZONE).toInstant(),
        date.plusDays(1).atStartOfDay(CheckInService.ZONE).toInstant(),
    )

    fun between(from: Instant, to: Instant): List<Nap> = repository.findBetween(from, to)

    fun delete(id: String) = repository.delete(id)

    private fun validate(start: Instant, end: Instant?) {
        if (end == null) return
        require(end.isAfter(start)) { "Het eind moet na het begin liggen" }
        require(Duration.between(start, end) <= MAX_DURATION) { "Een dutje duurt hooguit 12 uur" }
        require(!end.isAfter(clock.instant().plus(FUTURE_SLACK))) { "Het eind kan niet in de toekomst liggen" }
    }

    companion object {
        private val FUTURE_SLACK: Duration = Duration.ofMinutes(2)
        private val MAX_DURATION: Duration = Duration.ofHours(12)
        private val PENDING_WINDOW: Duration = Duration.ofDays(2)

        /** Verder dan dit na het begin zegt een teken van wakker zijn niets meer over het eind. */
        val MAX_ESTIMATED: Duration = Duration.ofHours(3)

        /** Zonder enig teken wordt een lopend dutje na deze tijd afgesloten. */
        val GIVE_UP_AFTER: Duration = Duration.ofHours(12)

        /** Aangenomen duur als er niets beters is. */
        val DEFAULT_DURATION: Duration = Duration.ofMinutes(30)
    }
}

data class NapRequest(
    val start: String? = null,
    val end: String? = null,
    val alarmAt: String? = null,
    val clearAlarm: Boolean = false,
)

data class NapDto(
    val id: String,
    val start: String,
    val end: String?,
    val endEstimated: Boolean,
    val alarmAt: String?,
)

data class NapDayDto(val date: String, val naps: List<NapDto>, val open: NapDto?)
data class NapsDto(val naps: List<NapDto>)

fun Nap.toDto() = NapDto(id, start.toString(), end?.toString(), endEstimated, alarmAt?.toString())

@RestController
class NapController(
    private val authService: AuthService,
    private val naps: NapService,
) {
    @PostMapping("/api/v1/naps")
    fun create(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: NapRequest,
    ): NapDto {
        authService.requireAuthorization(authorization)
        return handle { naps.create(instant(request.start), instant(request.end), instant(request.alarmAt)).toDto() }
    }

    @PutMapping("/api/v1/naps/{id}")
    fun update(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody request: NapRequest,
    ): NapDto {
        authService.requireAuthorization(authorization)
        return handle {
            naps.update(id, instant(request.start), instant(request.end), instant(request.alarmAt), request.clearAlarm).toDto()
        }
    }

    /** Dutjes van één dag (zonder `date` vandaag, Europe/Amsterdam) plus het dutje dat nu loopt. */
    @GetMapping("/api/v1/naps")
    fun day(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("date", required = false) date: String?,
    ): NapDayDto {
        authService.requireAuthorization(authorization)
        val day = runCatching { date?.let(LocalDate::parse) ?: LocalDate.now(CheckInService.ZONE) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige datum") }
        return NapDayDto(day.toString(), naps.day(day).map { it.toDto() }, naps.open()?.toDto())
    }

    /** Dutjes waarvan het eind nog ontbreekt of geschat is; de app probeert die bij elke sync te schatten. */
    @GetMapping("/api/v1/naps/pending")
    fun pending(@RequestHeader("Authorization", required = false) authorization: String?): NapsDto {
        authService.requireAuthorization(authorization)
        return NapsDto(naps.pending().map { it.toDto() })
    }

    @DeleteMapping("/api/v1/naps/{id}")
    fun delete(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        authService.requireAuthorization(authorization)
        naps.delete(id)
        return ResponseEntity.noContent().build()
    }

    private fun instant(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let {
        runCatching { Instant.parse(it) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldig tijdstip") }
    }

    private fun <T> handle(action: () -> T): T = try {
        action()
    } catch (e: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } catch (e: NapConflictException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
    } catch (e: NapNotFoundException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
    }
}
