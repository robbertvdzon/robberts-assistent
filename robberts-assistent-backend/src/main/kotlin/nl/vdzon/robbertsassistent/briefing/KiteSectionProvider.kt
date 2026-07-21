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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Kleurbeoordeling voor de kite-/strandfiets-briefingsecties. */
enum class RatingColor(val emoji: String) { GREEN("🟢"), YELLOW("🟡"), RED("🔴") }

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
        return AssessmentResult.Ok(assessed)
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
            windText = "${speedKn.toInt()} kn ($direction)",
            precipitationMm = precipitationMm,
            nearLowTide = nearLowTide,
            nearestLowTideAt = nearestLowTideAt,
            kite = kiteColor,
            beach = beachColor,
        )
    }

    private data class Slot(val label: String, val at: Instant)

    internal companion object {
        private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
    }
}

internal data class SlotAssessment(
    val label: String,
    val windKn: Int,
    val windText: String,
    val precipitationMm: Double,
    val nearLowTide: Boolean,
    val nearestLowTideAt: Instant?,
    val kite: RatingColor,
    val beach: RatingColor,
)

internal sealed interface AssessmentResult {
    data class Ok(val slots: List<SlotAssessment>) : AssessmentResult
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
) : BriefingSectionProvider {

    private val assessmentProvider = SlotAssessmentProvider(windForecastClient, weatherClient, tideClient, calendarClient)

    override val order = 0

    override fun section(): BriefingSection {
        val result = assessmentProvider.buildAssessments()
        val text = when (result) {
            is AssessmentResult.Error -> "Kon de kite-inschatting voor morgen niet maken: ${result.message}"
            is AssessmentResult.Ok -> result.slots.joinToString("\n") { slot ->
                "${slot.label}: ${slot.kite.emoji} ${slot.windText}"
            }
        }
        return BriefingSection(key = "kite", title = "Kiten", text = text)
    }

    /** Compacte one-liner voor de 18:00-push, gebaseerd op de laatste beoordeelde tijdsslot (avond, of het enige slot). */
    override fun shortSummary(): String? {
        val result = assessmentProvider.buildAssessments() as? AssessmentResult.Ok ?: return null
        val slot = result.slots.lastOrNull() ?: return null
        return "kiten ${slot.kite.emoji} ${slot.label.lowercase()} ${slot.windKn}kn"
    }

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
