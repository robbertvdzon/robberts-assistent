package nl.vdzon.robbertsassistent.insights

import java.time.Clock
import java.time.Duration
import java.time.Instant
import nl.vdzon.robbertsassistent.activity.ActivityService
import nl.vdzon.robbertsassistent.auth.AuthService
import nl.vdzon.robbertsassistent.nutrition.MealService
import nl.vdzon.robbertsassistent.wellbeing.CheckInService
import nl.vdzon.robbertsassistent.wellbeing.Nap
import nl.vdzon.robbertsassistent.wellbeing.NapDto
import nl.vdzon.robbertsassistent.wellbeing.NapNotFoundException
import nl.vdzon.robbertsassistent.wellbeing.NapService
import nl.vdzon.robbertsassistent.wellbeing.toDto
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Schat het eind van een dutje waarbij Robbert zich niet heeft afgemeld. Het eind is het vroegste
 * teken dat hij weer wakker was: de eerste stappen (die de app uit Health Connect meestuurt), het
 * begin van een wandeling of training, een foto van eten, een check-in, of de wekker die afging.
 * Garmin-gegevens komen soms pas uren later binnen; een eerdere schatting schuift dan nog naar
 * voren.
 */
@Service
class NapEstimator(
    private val naps: NapService,
    private val meals: MealService,
    private val checkIns: CheckInService,
    private val activity: ActivityService,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun estimate(id: String, movementAt: Instant?): Nap {
        val nap = naps.get(id)
        if (!nap.open && !nap.endEstimated) return nap
        return naps.applyEstimate(id, firstSign(nap, movementAt))
    }

    private fun firstSign(nap: Nap, movementAt: Instant?): Instant? {
        val now = clock.instant()
        val after = nap.start.plus(MIN_NAP)
        val until = minOf(now, nap.start.plus(Duration.ofDays(1)))
        if (!until.isAfter(after)) return null
        val zone = InsightService.ZONE
        val workouts = activity
            .between(nap.start.atZone(zone).toLocalDate(), until.atZone(zone).toLocalDate())
            .flatMap { it.workouts }
            .map { it.start }
        val signs = listOfNotNull(movementAt, nap.alarmAt) +
            workouts +
            // Het moment van vastleggen telt, niet het tijdstip dat bij de maaltijd is ingevuld.
            meals.between(nap.start.minus(Duration.ofDays(1)), until.plusSeconds(1)).map { it.createdAt } +
            checkIns.between(after, until.plusSeconds(1)).map { it.at }
        return signs.filter { !it.isBefore(after) && !it.isAfter(until) }.minOrNull()
    }

    companion object {
        /** Tekenen in de eerste minuten tellen niet: dan ligt hij nog niet te slapen. */
        private val MIN_NAP: Duration = Duration.ofMinutes(3)
    }
}

data class NapEstimateRequest(val movementAt: String? = null)

@RestController
class NapEstimateController(
    private val authService: AuthService,
    private val estimator: NapEstimator,
) {
    /**
     * De app roept dit na elke sync aan voor dutjes zonder (zeker) eind. `movementAt` is de eerste
     * minuut met stappen na het begin van het dutje, als de telefoon die kon vinden.
     */
    @PostMapping("/api/v1/naps/{id}/estimate")
    fun estimate(
        @RequestHeader("Authorization", required = false) authorization: String?,
        @PathVariable id: String,
        @RequestBody(required = false) request: NapEstimateRequest?,
    ): NapDto {
        authService.requireAuthorization(authorization)
        val movementAt = request?.movementAt?.takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }
                .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ongeldig tijdstip") }
        }
        return try {
            estimator.estimate(id, movementAt).toDto()
        } catch (e: NapNotFoundException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }
    }
}
