package nl.vdzon.robbertsassistent.zonneplan

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import nl.vdzon.robbertsassistent.auth.AuthService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/** Instraling op één dag: diffuus en direct zonlicht op een plat vlak, plus het aantal zonuren. */
data class DayRadiation(val diffuseKwhM2: Double, val directKwhM2: Double, val sunshineHours: Double?) {
    val totalKwhM2: Double get() = diffuseKwhM2 + directKwhM2
}

/** Weerbron voor de vergelijking: wat de zon per dag heeft geleverd op de plek van de panelen. */
interface SolarRadiationClient {
    fun daily(from: LocalDate, to: LocalDate): Map<LocalDate, DayRadiation>
}

/**
 * Open-Meteo (zonder sleutel): de uurwaarden van diffuse en directe instraling, opgeteld per dag.
 * De historische-voorspellingen-API loopt door tot en met vandaag en gaat jaren terug.
 */
@Component
class OpenMeteoSolarRadiationClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : SolarRadiationClient {

    override fun daily(from: LocalDate, to: LocalDate): Map<LocalDate, DayRadiation> {
        val url = "https://historical-forecast-api.open-meteo.com/v1/forecast" +
            "?latitude=$LATITUDE&longitude=$LONGITUDE" +
            "&hourly=diffuse_radiation,direct_radiation&daily=sunshine_duration" +
            "&start_date=$from&end_date=$to&timezone=Europe%2FAmsterdam"
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("Open-Meteo gaf HTTP ${response.statusCode()} terug.")
        return parse(jacksonObjectMapper().readTree(response.body()))
    }

    internal companion object {
        // Zelfde plek als de weersverwachting (`weather.OpenMeteoWeatherClient`).
        private const val LATITUDE = "52.5078"
        private const val LONGITUDE = "4.6420"

        /** Telt de uurwaarden (W/m², gemiddeld over het uur) op tot kWh/m² per dag. */
        internal fun parse(root: JsonNode): Map<LocalDate, DayRadiation> {
            val hourly = root.path("hourly")
            val diffuse = mutableMapOf<LocalDate, Double>()
            val direct = mutableMapOf<LocalDate, Double>()
            hourly.path("time").forEachIndexed { index, time ->
                val day = LocalDate.parse(time.asText().take(10))
                hourly.path("diffuse_radiation").get(index)?.takeIf { it.isNumber }
                    ?.let { diffuse.merge(day, it.asDouble() / 1000, Double::plus) }
                hourly.path("direct_radiation").get(index)?.takeIf { it.isNumber }
                    ?.let { direct.merge(day, it.asDouble() / 1000, Double::plus) }
            }
            val daily = root.path("daily")
            val sunshine = mutableMapOf<LocalDate, Double>()
            daily.path("time").forEachIndexed { index, time ->
                daily.path("sunshine_duration").get(index)?.takeIf { it.isNumber }
                    ?.let { sunshine[LocalDate.parse(time.asText())] = it.asDouble() / 3600 }
            }
            return diffuse.keys.intersect(direct.keys).associateWith { day ->
                DayRadiation(diffuse.getValue(day), direct.getValue(day), sunshine[day])
            }
        }
    }
}

enum class SolarVerdict { GOED, LET_OP, NIET, ONBEKEND }

/** Eén dag in de grafiek. [complete] is `false` voor vandaag: die dag loopt nog. */
data class SolarDay(
    val date: LocalDate,
    val yieldKwh: Double?,
    val expectedKwh: Double?,
    val radiationKwhM2: Double?,
    val sunshineHours: Double?,
    val complete: Boolean,
)

data class SolarOverview(
    val days: List<SolarDay>,
    val verdict: SolarVerdict,
    val verdictText: String,
    val weekYieldKwh: Double?,
    val weekExpectedKwh: Double?,
    val currentPowerWatt: Int? = null,
    val error: String? = null,
) {
    val weekPercent: Int?
        get() = if (weekYieldKwh != null && weekExpectedKwh != null && weekExpectedKwh > 0) {
            (100 * weekYieldKwh / weekExpectedKwh).roundToInt()
        } else {
            null
        }
}

/**
 * Vergelijkt de dagopbrengst met wat de panelen zelf bij dit weer plachten te leveren.
 *
 * Er is geen opgegeven vermogen of dakhelling nodig: de verwachting voor een dag komt uit de
 * dertig dagen ervoor, als `a × diffuus + b × direct` (kleinste kwadraten). Diffuus en direct
 * staan los omdat panelen die in de winter in de schaduw liggen wel diffuus licht vangen maar
 * nauwelijks direct; één getal per kWh/m² zou dan elk najaar vals alarm slaan. Het venster
 * schuift mee, dus het seizoen zit er vanzelf in.
 *
 * De weergegevens komen uit een weermodel en niet van het eigen dak. Per dag wijkt dat fors af;
 * daarom gaat het oordeel over zeven dagen samen en slaat het pas aan bij minder dan de helft.
 */
object SolarModel {
    private const val FIT_DAYS = 30
    private const val MIN_FIT_DAYS = 14
    private const val VERDICT_DAYS = 7
    private const val MIN_VERDICT_DAYS = 5
    private const val WARN_BELOW = 0.5
    private const val FAIL_BELOW = 0.3

    /** Onder deze dagopbrengst (kWh) op een dag met zon doet de omvormer het niet. */
    private const val DEAD_BELOW_KWH = 0.1
    private const val DEAD_NEEDS_EXPECTED_KWH = 0.5

    fun assess(
        yields: Map<LocalDate, Double>,
        radiation: Map<LocalDate, DayRadiation>,
        today: LocalDate,
    ): SolarOverview {
        val first = yields.keys.minOrNull()
            ?: return SolarOverview(emptyList(), SolarVerdict.ONBEKEND, "Geen dagopbrengsten beschikbaar.", null, null)
        val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val known = dates.filter { it.isBefore(today) && yields[it] != null && radiation[it] != null }
        val days = dates.map { date ->
            val weather = radiation[date]
            val window = known.filter { it.isBefore(date) }.takeLast(FIT_DAYS)
            val expected = if (weather != null && window.size >= MIN_FIT_DAYS) {
                val (a, b) = fit(window.map { Triple(yields.getValue(it), radiation.getValue(it).diffuseKwhM2, radiation.getValue(it).directKwhM2) })
                round2(a * weather.diffuseKwhM2 + b * weather.directKwhM2)
            } else {
                null
            }
            SolarDay(
                date = date,
                yieldKwh = yields[date]?.let(::round2),
                expectedKwh = expected,
                radiationKwhM2 = weather?.totalKwhM2?.let(::round2),
                sunshineHours = weather?.sunshineHours?.let { (it * 10).roundToInt() / 10.0 },
                complete = date.isBefore(today),
            )
        }
        return verdict(days)
    }

    private fun verdict(days: List<SolarDay>): SolarOverview {
        val week = days.filter { it.complete && it.yieldKwh != null && it.expectedKwh != null }.takeLast(VERDICT_DAYS)
        if (week.size < MIN_VERDICT_DAYS) {
            return SolarOverview(
                days, SolarVerdict.ONBEKEND,
                "Nog te weinig dagen met opbrengst en weer om te vergelijken.", null, null,
            )
        }
        val actual = round2(week.sumOf { it.yieldKwh!! })
        val expected = round2(week.sumOf { it.expectedKwh!! })
        val ratio = if (expected > 0) actual / expected else 1.0
        val last = week.last()
        val dead = last.yieldKwh!! < DEAD_BELOW_KWH && last.expectedKwh!! >= DEAD_NEEDS_EXPECTED_KWH
        val verdict = when {
            dead || ratio < FAIL_BELOW -> SolarVerdict.NIET
            ratio < WARN_BELOW -> SolarVerdict.LET_OP
            else -> SolarVerdict.GOED
        }
        val percent = (ratio * 100).roundToInt()
        val text = if (dead) {
            "Op ${last.date} vrijwel niets opgewekt (${kwh(last.yieldKwh)}) terwijl ${kwh(last.expectedKwh!!)} verwacht was."
        } else {
            "Afgelopen ${week.size} dagen ${kwh(actual)} opgewekt; bij dit weer verwacht ${kwh(expected)} ($percent%)."
        }
        return SolarOverview(days, verdict, text, actual, expected)
    }

    /**
     * Kleinste kwadraten voor `opbrengst = a × diffuus + b × direct`, met beide factoren niet
     * negatief: een negatieve factor heeft geen betekenis en valt terug op één factor.
     */
    internal fun fit(samples: List<Triple<Double, Double, Double>>): Pair<Double, Double> {
        val dd = samples.sumOf { it.second * it.second }
        val bb = samples.sumOf { it.third * it.third }
        val db = samples.sumOf { it.second * it.third }
        val dy = samples.sumOf { it.second * it.first }
        val by = samples.sumOf { it.third * it.first }
        val onlyDiffuse = (if (dd > 0) dy / dd else 0.0) to 0.0
        val onlyDirect = 0.0 to (if (bb > 0) by / bb else 0.0)
        val det = dd * bb - db * db
        if (abs(det) < 1e-9) return if (dd > 0) onlyDiffuse else onlyDirect
        val a = (dy * bb - by * db) / det
        val b = (by * dd - dy * db) / det
        return when {
            a < 0 -> onlyDirect
            b < 0 -> onlyDiffuse
            else -> a to b
        }
    }

    private fun round2(value: Double) = (value * 100).roundToInt() / 100.0
    private fun kwh(value: Double) = "%.1f kWh".format(java.util.Locale.forLanguageTag("nl"), value)
}

/**
 * Het overzicht van de zonnepanelen: dagopbrengsten uit Home Assistant naast het weer. Een half
 * uur gecachet, want zowel de health check als het grafiekscherm vragen het op.
 */
@Service
class SolarService(
    private val zonneplan: ZonneplanClient,
    private val radiation: SolarRadiationClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cached: Pair<Instant, SolarOverview>? = null

    fun overview(refresh: Boolean = false): SolarOverview {
        val now = clock.instant()
        cached?.takeIf { !refresh && Duration.between(it.first, now) < CACHE }?.let { return it.second }
        val fresh = runCatching { build(now) }.getOrElse {
            logger.warn("Zonnepanelen-overzicht opbouwen mislukt: {}", it.message)
            SolarOverview(
                emptyList(), SolarVerdict.ONBEKEND, "Kon de opbrengst niet vergelijken met het weer.", null, null,
                error = it.message ?: it.javaClass.simpleName,
            )
        }
        // Een mislukte poging niet vasthouden: de volgende aanvraag probeert het opnieuw.
        if (fresh.error == null) cached = now to fresh
        return fresh
    }

    private fun build(now: Instant): SolarOverview {
        val today = now.atZone(ZONE).toLocalDate()
        val since = today.minusDays(HISTORY_DAYS)
        val yields = zonneplan.dailyYields(since).filterKeys { !it.isBefore(since) && !it.isAfter(today) }
        if (yields.isEmpty()) {
            return SolarOverview(emptyList(), SolarVerdict.ONBEKEND, "Geen dagopbrengsten beschikbaar.", null, null)
        }
        val weather = radiation.daily(yields.keys.min(), today)
        val power = runCatching { zonneplan.status().currentPowerWatt }.getOrNull()
        return SolarModel.assess(yields, weather, today).copy(currentPowerWatt = power).also {
            logger.info("Zonnepanelen: {} dagen, oordeel {} ({}% van verwacht)", it.days.size, it.verdict, it.weekPercent)
        }
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val CACHE: Duration = Duration.ofMinutes(30)
        const val HISTORY_DAYS = 400L
    }
}

data class SolarDayDto(
    val date: String,
    val yieldKwh: Double?,
    val expectedKwh: Double?,
    val radiationKwhM2: Double?,
    val sunshineHours: Double?,
    val complete: Boolean,
)

data class SolarOverviewDto(
    val days: List<SolarDayDto>,
    val verdict: String,
    val verdictText: String,
    val weekYieldKwh: Double?,
    val weekExpectedKwh: Double?,
    val weekPercent: Int?,
    val currentPowerWatt: Int?,
    val error: String?,
)

@RestController
class SolarController(private val authService: AuthService, private val solarService: SolarService) {
    @GetMapping("/api/v1/solar")
    fun solar(@RequestHeader("Authorization", required = false) authorization: String?): SolarOverviewDto {
        authService.requireAuthorization(authorization)
        val overview = solarService.overview()
        return SolarOverviewDto(
            days = overview.days.map {
                SolarDayDto(it.date.toString(), it.yieldKwh, it.expectedKwh, it.radiationKwhM2, it.sunshineHours, it.complete)
            },
            verdict = overview.verdict.name,
            verdictText = overview.verdictText,
            weekYieldKwh = overview.weekYieldKwh,
            weekExpectedKwh = overview.weekExpectedKwh,
            weekPercent = overview.weekPercent,
            currentPowerWatt = overview.currentPowerWatt,
            error = overview.error,
        )
    }
}
