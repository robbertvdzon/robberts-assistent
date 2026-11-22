package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.tides.TideClient
import nl.vdzon.robbertsassistent.tides.TideForecast
import nl.vdzon.robbertsassistent.tides.TideType
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WeatherForecast
import nl.vdzon.robbertsassistent.weather.WindForecast
import nl.vdzon.robbertsassistent.weather.WindForecastClient
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Kleurbeoordeling voor de kite-/strandfiets-briefingsecties. */
enum class RatingColor(val emoji: String) { GREEN("🟢"), YELLOW("🟡"), RED("🔴") }

internal fun RatingColor.toBriefingStatus(): BriefingStatus = when (this) {
    RatingColor.GREEN -> BriefingStatus.GOED
    RatingColor.YELLOW -> BriefingStatus.LET_OP
    RatingColor.RED -> BriefingStatus.NIET
}

private fun RatingColor.priority(): Int = when (this) {
    RatingColor.GREEN -> 0
    RatingColor.YELLOW -> 1
    RatingColor.RED -> 2
}

/** [slots] staat in dagdeelvolgorde; `minByOrNull` houdt bij gelijke prioriteit het eerste slot. */
internal fun bestSlot(slots: List<SlotAssessment>, rating: (SlotAssessment) -> RatingColor): SlotAssessment? =
    slots.minByOrNull { rating(it).priority() }

private val STALE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))

/**
 * Toevoeging voor de sectieteksten als de onderliggende weer-/winddata uit de last-known-good komt
 * (zie `weather.ForecastFetcher`): de normale inhoud blijft staan, met erachter het ophaalmoment.
 */
internal fun staleNotice(fetchedAt: Instant): String = "(gegevens van ${STALE_TIME_FORMATTER.format(fetchedAt)})"

/** Het oudste ophaalmoment van de verouderde bronnen, of `null` als geen van beide verouderd is. */
internal fun oldestStaleMoment(wind: WindForecast, weather: WeatherForecast): Instant? =
    listOfNotNull(
        wind.fetchedAt.takeIf { wind.stale },
        weather.fetchedAt.takeIf { weather.stale },
    ).minOrNull()

/**
 * Gedeelde dagdeel-beoordeling voor de kite- en strandfiets-briefingsecties ([KiteSectionProvider],
 * [BeachCycleSectionProvider]): combineert een gestructureerde windvoorspelling
 * ([WindForecastClient], Wijk aan Zee), neerslag ([WeatherClient]) en laagwatertijden
 * ([TideClient], IJmuiden) tot per-dagdeel data voor morgen (de eerstvolgende dag), zodat beide
 * secties dezelfde netwerkcalls en dagdeel-/werkdag-/vakantielogica hergebruiken i.p.v. dupliceren.
 * Beoordeelt ochtend (07:00) en avond (19:00) apart op een werkdag (ma-do, geen feestdag/vakantie)
 * — anders één dagbeoordeling (12:00).
 */
internal class SlotAssessmentProvider(
    private val windForecastClient: WindForecastClient,
    private val weatherClient: WeatherClient,
    private val tideClient: TideClient,
    private val calendarClient: CalendarClient,
) {

    fun buildAssessments(): AssessmentResult {
        val zone = ZoneId.of("Europe/Amsterdam")
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val vacation = isVacationDay(tomorrow, zone)
        val slots = assessmentSlots(tomorrow, zone, vacation)

        val wind = windForecastClient.hourlyForecast(72)
        val weather = weatherClient.hourlyForecast(72)
        val tide = tideClient.forecast(72)

        val error = wind.error ?: weather.error ?: tide.error
        if (error != null) return AssessmentResult.Error(error)

        val assessed = slots.mapNotNull { slot -> assessSlot(slot, wind, weather, tide) }
        if (assessed.isEmpty()) return AssessmentResult.Error("geen voorspellingsdata beschikbaar.")
        return AssessmentResult.Ok(assessed, staleSince = oldestStaleMoment(wind, weather))
    }

    private fun isVacationDay(date: LocalDate, zone: ZoneId): Boolean {
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        return calendarClient.eventsInRange(from, to).any { it.allDay }
    }

    /** Werkdag (ma-do, geen feestdag, geen vakantiedag) => ochtend+avond; anders één dagbeoordeling. */
    private fun assessmentSlots(date: LocalDate, zone: ZoneId, vacation: Boolean): List<Slot> {
        val isWorkday = date.dayOfWeek in WORKDAYS && !Holidays.isHoliday(date) && !vacation
        return if (isWorkday) {
            listOf(
                Slot("Ochtend", date.atTime(7, 0).atZone(zone).toInstant()),
                Slot("Avond", date.atTime(19, 0).atZone(zone).toInstant()),
            )
        } else {
            listOf(Slot("Dag", date.atTime(12, 0).atZone(zone).toInstant()))
        }
    }

    private fun assessSlot(slot: Slot, wind: WindForecast, weather: WeatherForecast, tide: TideForecast): SlotAssessment? {
        val windHour = wind.hours.minByOrNull { Duration.between(it.time, slot.at).abs() }
        val weatherHour = weather.hours.minByOrNull { Duration.between(it.time, slot.at).abs() }
        if (windHour == null || weatherHour == null) return null
        val speedKn = windHour.speedKn
        val precipitationMm = weatherHour.precipitationMm
        val nearLowTide = KiteSectionProvider.isNearLowTide(slot.at, tide)
        val nearestLowTideAt = tide.extremes.filter { it.type == TideType.LAAGWATER }
            .minByOrNull { Duration.between(it.time, slot.at).abs() }?.time

        val kiteColor = KiteSectionProvider.assessKite(speedKn, windHour.directionDeg, precipitationMm)
        val beachColor = KiteSectionProvider.assessBeachCycle(speedKn, precipitationMm, nearLowTide)
        val direction = KiteSectionProvider.compassPoint(windHour.directionDeg)

        return SlotAssessment(
            label = slot.label,
            windKn = speedKn.toInt(),
            windDirection = direction,
            windText = "${speedKn.toInt()} kn ($direction)",
            precipitationMm = precipitationMm,
            nearLowTide = nearLowTide,
            nearestLowTideAt = nearestLowTideAt,
            kite = kiteColor,
            beach = beachColor,
            kiteReason = KiteSectionProvider.kiteReason(speedKn, windHour.directionDeg, precipitationMm),
            beachReason = KiteSectionProvider.beachReason(speedKn, precipitationMm, nearLowTide),
        )
    }

    /** Kiten morgen, alleen op een vrije dag (vr/za/zo) en alleen als het kan. */
    fun kiteOutlook(today: LocalDate): String? {
        val tomorrow = today.plusDays(1)
        if (!FreeDayOutlook.isFreeDay(tomorrow)) return null
        val wind = windForecastClient.hourlyForecast(72)
        val weather = weatherClient.hourlyForecast(72)
        if (wind.error != null || weather.error != null) return null
        return FreeDayOutlook.kiteWindow(tomorrow, ZONE, wind, weather)?.let(FreeDayOutlook::kiteSummary)
    }

    /** Strandfietsen morgen, alleen op een vrije dag en alleen als het kan, met het beste moment. */
    fun beachOutlook(today: LocalDate): String? {
        val tomorrow = today.plusDays(1)
        if (!FreeDayOutlook.isFreeDay(tomorrow)) return null
        val wind = windForecastClient.hourlyForecast(72)
        val weather = weatherClient.hourlyForecast(72)
        val tide = tideClient.forecast(72)
        if (wind.error != null || weather.error != null || tide.error != null) return null
        return FreeDayOutlook.beachMoments(tomorrow, ZONE, wind, weather, tide)
            .takeIf { it.isNotEmpty() }?.let { FreeDayOutlook.beachSummary(it, ZONE) }
    }

    private data class Slot(val label: String, val at: Instant)

    internal companion object {
        private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        internal val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
    }
}

/** Een aaneengesloten stuk van de dag waarop kiten kan. */
internal data class KiteWindow(val fromHour: Int, val toHour: Int, val windKn: Int, val direction: String, val color: RatingColor)

/**
 * Wat er morgen buiten kan op een vrije dag, voor de 18:00-push. Robbert kan alleen op vrijdag,
 * zaterdag en zondag kiten en strandfietsen, en dan de hele dag; de push noemt het alleen als het
 * kan. Voor het fietsen is het beste moment 3,5 uur vóór hoogwater.
 */
internal object FreeDayOutlook {
    private val FREE_DAYS = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val KITE_HOURS = 9..17
    private val BEACH_HOURS = 8..19
    private const val MIN_KITE_HOURS = 2
    private val BEFORE_HIGH_WATER: Duration = Duration.ofMinutes(210)
    private val NEAREST_HOUR: Duration = Duration.ofMinutes(90)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun isFreeDay(date: LocalDate): Boolean = date.dayOfWeek in FREE_DAYS

    /**
     * Het langste aaneengesloten stuk van minstens twee uur waarop kiten goed kan (groen); is dat
     * er niet, dan het langste stuk waarop het met ander materiaal kan (geel of groen).
     */
    fun kiteWindow(date: LocalDate, zone: ZoneId, wind: WindForecast, weather: WeatherForecast): KiteWindow? {
        val hours = KITE_HOURS.mapNotNull { hour ->
            val at = date.atTime(hour, 0).atZone(zone).toInstant()
            val w = wind.hours.nearest(at) { it.time } ?: return@mapNotNull null
            val rain = weather.hours.nearest(at) { it.time } ?: return@mapNotNull null
            Triple(hour, w, KiteSectionProvider.assessKite(w.speedKn, w.directionDeg, rain.precipitationMm))
        }
        fun longest(accept: Set<RatingColor>): List<Triple<Int, nl.vdzon.robbertsassistent.weather.HourlyWind, RatingColor>> {
            var best = emptyList<Triple<Int, nl.vdzon.robbertsassistent.weather.HourlyWind, RatingColor>>()
            var run = mutableListOf<Triple<Int, nl.vdzon.robbertsassistent.weather.HourlyWind, RatingColor>>()
            for (hour in hours) {
                if (hour.third in accept && (run.isEmpty() || run.last().first + 1 == hour.first)) {
                    run += hour
                } else {
                    run = if (hour.third in accept) mutableListOf(hour) else mutableListOf()
                }
                if (run.size > best.size) best = run.toList()
            }
            return best
        }
        val green = longest(setOf(RatingColor.GREEN))
        val run = if (green.size >= MIN_KITE_HOURS) green else longest(setOf(RatingColor.GREEN, RatingColor.YELLOW))
        if (run.size < MIN_KITE_HOURS) return null
        val strongest = run.maxBy { it.second.speedKn }.second
        return KiteWindow(
            fromHour = run.first().first,
            toHour = run.last().first + 1,
            windKn = run.map { it.second.speedKn }.average().toInt(),
            direction = KiteSectionProvider.compassPoint(strongest.directionDeg),
            color = if (run === green) RatingColor.GREEN else RatingColor.YELLOW,
        )
    }

    /** De momenten 3,5 uur vóór hoogwater die op [date] overdag vallen en waarop het droog en rustig is. */
    fun beachMoments(date: LocalDate, zone: ZoneId, wind: WindForecast, weather: WeatherForecast, tide: TideForecast): List<Instant> =
        tide.extremes.filter { it.type == TideType.HOOGWATER }
            .map { it.time.minus(BEFORE_HIGH_WATER) }
            .filter { at ->
                val local = at.atZone(zone)
                local.toLocalDate() == date && local.hour in BEACH_HOURS
            }
            .filter { at ->
                val w = wind.hours.nearest(at) { it.time } ?: return@filter false
                val rain = weather.hours.nearest(at) { it.time } ?: return@filter false
                KiteSectionProvider.assessBeachCycle(w.speedKn, rain.precipitationMm, nearLowTide = true) == RatingColor.GREEN
            }
            .sorted()

    fun kiteSummary(window: KiteWindow): String =
        "kiten kan${if (window.color == RatingColor.YELLOW) " misschien" else ""} " +
            "${window.fromHour}–${window.toHour} uur (${window.windKn} kn ${window.direction})"

    fun beachSummary(moments: List<Instant>, zone: ZoneId): String =
        "strandfietsen kan, beste moment " + moments.joinToString(" of ") { TIME.format(it.atZone(zone)) }

    private fun <T> List<T>.nearest(at: Instant, time: (T) -> Instant): T? =
        minByOrNull { Duration.between(time(it), at).abs() }?.takeIf { Duration.between(time(it), at).abs() <= NEAREST_HOUR }
}

/** De reden voor de tegel; met meerdere dagdelen staat erbij over welk dagdeel het gaat. */
internal fun reason(label: String, reason: String, slots: Int): String =
    if (slots > 1) "$label: ${reason.replaceFirstChar { it.lowercase() }}" else reason

internal data class SlotAssessment(
    val label: String,
    val windKn: Int,
    val windDirection: String,
    val windText: String,
    val precipitationMm: Double,
    val nearLowTide: Boolean,
    val nearestLowTideAt: Instant?,
    val kite: RatingColor,
    val beach: RatingColor,
    val kiteReason: String? = null,
    val beachReason: String? = null,
)

internal sealed interface AssessmentResult {
    /**
     * [staleSince] is gezet als de wind- en/of weerdata uit de last-known-good komt: het oudste
     * ophaalmoment van de twee, zodat kiten en strandfietsen allebei `(gegevens van HH:MM)` kunnen
     * tonen. Bij verse of TTL-gecachete data is het `null`.
     */
    data class Ok(val slots: List<SlotAssessment>, val staleSince: Instant? = null) : AssessmentResult
    data class Error(val message: String) : AssessmentResult
}

/**
 * Kite-briefingsectie voor morgen: per dagdeel de kiten-inschatting op basis van de gedeelde
 * [SlotAssessmentProvider]. Zie klasse-KDoc daar voor de onderliggende dataproviders en
 * dagdeel-/werkdag-/vakantielogica.
 */
@Component
class KiteSectionProvider(
    windForecastClient: WindForecastClient,
    weatherClient: WeatherClient,
    tideClient: TideClient,
    calendarClient: CalendarClient,
    private val clock: Clock = Clock.systemUTC(),
) : BriefingSectionProvider {

    private val assessmentProvider = SlotAssessmentProvider(windForecastClient, weatherClient, tideClient, calendarClient)

    override val order = 0

    override fun section(): BriefingSection {
        val result = assessmentProvider.buildAssessments()
        return when (result) {
            is AssessmentResult.Error -> BriefingSection(
                key = "kite",
                title = "Kiten",
                text = "Kon de kite-inschatting voor morgen niet maken: ${result.message}",
            )
            is AssessmentResult.Ok -> {
                val slot = bestSlot(result.slots) { it.kite }
                BriefingSection(
                    key = "kite",
                    title = "Kiten",
                    text = result.slots.joinToString("\n") { item ->
                        "${item.label}: ${item.kite.emoji} ${item.windText}"
                    } + (result.staleSince?.let { "\n${staleNotice(it)}" } ?: ""),
                    status = slot?.kite?.toBriefingStatus(),
                    tileLabel = slot?.let { "${it.windKn} kn ${it.windDirection}" },
                    statusReason = slot?.kiteReason?.let { reason(slot.label, it, result.slots.size) },
                )
            }
        }
    }

    /** Voor de 18:00-push: alleen als morgen vrijdag, zaterdag of zondag is én kiten dan kan. */
    override fun shortSummary(): String? =
        assessmentProvider.kiteOutlook(LocalDate.now(clock.withZone(SlotAssessmentProvider.ZONE)))

    internal companion object {

        // Wijk aan Zee: kust loopt ~noord-zuid, zee ligt ten westen. "Aanlandig" = wind komt uit het
        // westelijke kwadrant (ZW t/m NW) — zee op naar het strand.
        private val ONSHORE_SECTOR = 225.0..315.0
        internal const val DRY_THRESHOLD_MM = 0.1
        private val KITE_IDEAL_RANGE = 20.0..35.0
        // Net buiten het ideale bereik, maar nog wel bruikbaar met het juiste materiaal (grotere/
        // kleinere kite) — vandaar geel i.p.v. rood.
        private val KITE_BORDERLINE_LOW = 15.0..<20.0
        private val KITE_BORDERLINE_HIGH = 35.0..45.0
        private const val BEACH_CYCLE_MAX_WIND_KN = 15.0
        // "Redelijk laag water": binnen dit venster rond een laagwatermoment.
        private val LOW_TIDE_WINDOW = Duration.ofHours(2)

        internal fun isNearLowTide(at: Instant, tide: TideForecast): Boolean =
            tide.extremes.filter { it.type == TideType.LAAGWATER }
                .minOfOrNull { Duration.between(it.time, at).abs() }
                ?.let { it <= LOW_TIDE_WINDOW }
                ?: false

        internal fun assessKite(speedKn: Double, directionDeg: Double, precipitationMm: Double): RatingColor {
            val dry = precipitationMm <= DRY_THRESHOLD_MM
            val onshore = directionDeg in ONSHORE_SECTOR
            if (!dry || !onshore) return RatingColor.RED
            return when {
                speedKn in KITE_IDEAL_RANGE -> RatingColor.GREEN
                speedKn in KITE_BORDERLINE_LOW || speedKn in KITE_BORDERLINE_HIGH -> RatingColor.YELLOW
                else -> RatingColor.RED
            }
        }

        /** Waarom kiten geel of rood is, in gewone woorden; `null` bij groen. */
        internal fun kiteReason(speedKn: Double, directionDeg: Double, precipitationMm: Double): String? {
            val reasons = buildList {
                if (precipitationMm > DRY_THRESHOLD_MM) add("regen")
                if (directionDeg !in ONSHORE_SECTOR) add("de wind is niet aanlandig (${compassPoint(directionDeg)})")
                when {
                    speedKn < KITE_BORDERLINE_LOW.start -> add("te weinig wind (${speedKn.toInt()} kn, kiten kan vanaf 15)")
                    speedKn in KITE_BORDERLINE_LOW -> add("weinig wind (${speedKn.toInt()} kn, ideaal is 20 tot 35)")
                    speedKn in KITE_IDEAL_RANGE -> Unit
                    speedKn <= KITE_BORDERLINE_HIGH.endInclusive -> add("veel wind (${speedKn.toInt()} kn, ideaal is 20 tot 35)")
                    else -> add("te veel wind (${speedKn.toInt()} kn)")
                }
            }
            return reasons.takeIf { it.isNotEmpty() }?.joinToString(" en ")?.replaceFirstChar { it.uppercase() }
        }

        /** Waarom strandfietsen geel of rood is; `null` bij groen. */
        internal fun beachReason(speedKn: Double, precipitationMm: Double, nearLowTide: Boolean): String? {
            val reasons = buildList {
                if (precipitationMm > DRY_THRESHOLD_MM) add("regen")
                if (speedKn >= BEACH_CYCLE_MAX_WIND_KN) add("te veel wind (${speedKn.toInt()} kn, fietsen kan tot 15)")
                if (!nearLowTide) add("het is dan geen laagwater")
            }
            return reasons.takeIf { it.isNotEmpty() }?.joinToString(" en ")?.replaceFirstChar { it.uppercase() }
        }

        internal fun assessBeachCycle(speedKn: Double, precipitationMm: Double, nearLowTide: Boolean): RatingColor {
            val dry = precipitationMm <= DRY_THRESHOLD_MM
            val lowWind = speedKn < BEACH_CYCLE_MAX_WIND_KN
            if (!dry || !lowWind) return RatingColor.RED
            return if (nearLowTide) RatingColor.GREEN else RatingColor.YELLOW
        }

        private val COMPASS_POINTS = listOf(
            "N", "NNO", "NO", "ONO", "O", "OZO", "ZO", "ZZO",
            "Z", "ZZW", "ZW", "WZW", "W", "WNW", "NW", "NNW",
        )

        internal fun compassPoint(degrees: Double): String =
            COMPASS_POINTS[(((degrees % 360) / 22.5) + 0.5).toInt() % 16]
    }
}
