package nl.vdzon.robbertsassistent.activity

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.couplings.CouplingProbe
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Service
class ActivityService(
    private val repository: ActivityDayRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Neemt een batch dagen van de telefoon over. Per dag vervangt de sync alles wat van Garmin
     * komt; het handmatig ingevoerde gewicht blijft staan als de sync er geen meestuurt.
     */
    fun sync(days: List<ActivityDay>, source: String): List<ActivityDay> {
        val now = clock.instant()
        return days.map { incoming ->
            val existing = repository.find(incoming.date)
            repository.save(
                incoming.copy(
                    weightKg = incoming.weightKg ?: existing?.weightKg,
                    source = source,
                    syncedAt = now,
                ),
            )
        }
    }

    fun setWeight(date: LocalDate, weightKg: Double): ActivityDay {
        require(weightKg in 30.0..250.0) { "Gewicht moet tussen 30 en 250 kg liggen" }
        val existing = repository.find(date) ?: ActivityDay(date = date)
        return repository.save(existing.copy(weightKg = weightKg))
    }

    fun day(date: LocalDate): ActivityDay? = repository.find(date)

    fun between(from: LocalDate, to: LocalDate): List<ActivityDay> = repository.findBetween(from, to)

    fun lastSyncedAt(): Instant? = repository.lastSynced()?.syncedAt

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}

/**
 * Koppelingsstatus voor Garmin via Health Connect. De backend haalt zelf niets op: de app op
 * Robberts telefoon leest Health Connect uit en stuurt de dagen hierheen. "Werkt" betekent dus dat
 * er recent een sync is binnengekomen.
 */
@Component
class HealthConnectCouplingProbe(
    private val activity: ActivityService,
    private val clock: Clock = Clock.systemUTC(),
) : CouplingProbe {
    override val id = "health-connect"
    override val name = "Garmin (Health Connect)"
    override val description = "Stappen, verbruik, trainingen en slaap; aangeleverd door de app op de telefoon."
    override val configured: Boolean get() = activity.lastSyncedAt() != null
    override val mode: String get() = if (configured) "echt" else "fallback"

    override fun test(): Pair<Boolean, String> {
        val last = activity.lastSyncedAt() ?: return false to "nog nooit gesynct; open het scherm Eten in de app op je telefoon"
        val age = Duration.between(last, clock.instant())
        val text = when {
            age.toMinutes() < 90 -> "laatste sync ${age.toMinutes()} min geleden"
            age.toHours() < 48 -> "laatste sync ${age.toHours()} uur geleden"
            else -> "laatste sync ${age.toDays()} dagen geleden"
        }
        return (age < STALE_AFTER) to text
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofHours(36)
    }
}

data class SleepDto(
    val start: String = "",
    val end: String = "",
    val minutes: Int = 0,
    val deepMinutes: Int = 0,
    val lightMinutes: Int = 0,
    val remMinutes: Int = 0,
    val awakeMinutes: Int = 0,
)

data class WorkoutDto(
    val start: String = "",
    val end: String = "",
    val type: String = "",
    val kcal: Int? = null,
    val distanceM: Int? = null,
)

data class ActivityDayDto(
    val date: String = "",
    val steps: Int? = null,
    val totalKcal: Int? = null,
    val distanceM: Int? = null,
    val floors: Int? = null,
    val restingHeartRate: Int? = null,
    val sleep: SleepDto? = null,
    val workouts: List<WorkoutDto> = emptyList(),
    val weightKg: Double? = null,
    val syncedAt: String? = null,
)

data class ActivitySyncRequest(val source: String = "health-connect", val days: List<ActivityDayDto> = emptyList())
data class ActivityDaysDto(val days: List<ActivityDayDto>, val lastSyncedAt: String?)
data class WeightRequest(val date: String? = null, val weightKg: Double = 0.0)

@RestController
class ActivityController(
    private val authService: AuthService,
    private val activity: ActivityService,
) {
    /** De app stuurt de laatste dagen uit Health Connect; per datum wordt de dag vervangen. */
    @PostMapping("/api/v1/activity/days")
    fun sync(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: ActivitySyncRequest,
    ): ActivityDaysDto {
        authService.requireAuthorization(authorization)
        if (request.days.size > MAX_DAYS) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Hooguit $MAX_DAYS dagen per keer")
        val days = try {
            request.days.map { it.toDay() }
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige dag in de sync: ${e.message}")
        }
        return ActivityDaysDto(activity.sync(days, request.source.take(40)).map { it.toDto() }, activity.lastSyncedAt()?.toString())
    }

    /** Dagen in een bereik; zonder parameters alleen vandaag (Europe/Amsterdam). */
    @GetMapping("/api/v1/activity/days")
    fun days(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("to", required = false) to: String?,
    ): ActivityDaysDto {
        authService.requireAuthorization(authorization)
        val today = LocalDate.now(ActivityService.ZONE)
        val (start, end) = try {
            val s = from?.let(LocalDate::parse) ?: today
            s to (to?.let(LocalDate::parse) ?: s)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige datum")
        }
        return ActivityDaysDto(activity.between(start, end).map { it.toDto() }, activity.lastSyncedAt()?.toString())
    }

    @PutMapping("/api/v1/activity/weight")
    fun weight(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @RequestBody request: WeightRequest,
    ): ActivityDayDto {
        authService.requireAuthorization(authorization)
        return try {
            val date = request.date?.let(LocalDate::parse) ?: LocalDate.now(ActivityService.ZONE)
            activity.setWeight(date, request.weightKg).toDto()
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        } catch (e: java.time.format.DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldige datum")
        }
    }

    private companion object {
        const val MAX_DAYS = 31
    }
}

private fun ActivityDayDto.toDay() = ActivityDay(
    date = LocalDate.parse(date),
    steps = steps,
    totalKcal = totalKcal,
    distanceM = distanceM,
    floors = floors,
    restingHeartRate = restingHeartRate,
    sleep = sleep?.let {
        SleepSummary(Instant.parse(it.start), Instant.parse(it.end), it.minutes, it.deepMinutes, it.lightMinutes, it.remMinutes, it.awakeMinutes)
    },
    workouts = workouts.map { Workout(Instant.parse(it.start), Instant.parse(it.end), it.type, it.kcal, it.distanceM) },
    weightKg = weightKg,
)

private fun ActivityDay.toDto() = ActivityDayDto(
    date = date.toString(),
    steps = steps,
    totalKcal = totalKcal,
    distanceM = distanceM,
    floors = floors,
    restingHeartRate = restingHeartRate,
    sleep = sleep?.let {
        SleepDto(it.start.toString(), it.end.toString(), it.minutes, it.deepMinutes, it.lightMinutes, it.remMinutes, it.awakeMinutes)
    },
    workouts = workouts.map { WorkoutDto(it.start.toString(), it.end.toString(), it.type, it.kcal, it.distanceM) },
    weightKg = weightKg,
    syncedAt = syncedAt?.toString(),
)
