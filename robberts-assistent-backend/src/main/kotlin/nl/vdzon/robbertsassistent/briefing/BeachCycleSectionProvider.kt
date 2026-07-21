package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.google.CalendarClient
import nl.vdzon.robbertsassistent.tides.TideClient
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WindForecastClient
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Strandfiets-briefingsectie voor morgen: per dagdeel een bolletje (🟢/🟡/🔴) MET onderbouwing
 * (wind, regen, getij) zodat het oordeel navolgbaar is — i.p.v. alleen het bolletje zoals de kite-
 * sectie. Hergebruikt dezelfde gedeelde [SlotAssessmentProvider] als [KiteSectionProvider] (geen
 * dubbele netwerkcalls of gedupliceerde dagdeel-/werkdag-/vakantielogica).
 */
@Component
class BeachCycleSectionProvider(
    windForecastClient: WindForecastClient,
    weatherClient: WeatherClient,
    tideClient: TideClient,
    calendarClient: CalendarClient,
) : BriefingSectionProvider {

    private val assessmentProvider = SlotAssessmentProvider(windForecastClient, weatherClient, tideClient, calendarClient)

    override val order = 5

    override fun section(): BriefingSection {
        val result = assessmentProvider.buildAssessments()
        val text = when (result) {
            is AssessmentResult.Error -> "Kon de strandfietsinschatting voor morgen niet maken: ${result.message}"
            is AssessmentResult.Ok -> result.slots.joinToString("\n") { slot ->
                "${slot.label}: ${slot.beach.emoji} (${slot.windText}, ${rainText(slot.precipitationMm)}, ${tideText(slot)})"
            }
        }
        return BriefingSection(key = "beach", title = "Strandfietsen", text = text)
    }

    private fun rainText(precipitationMm: Double): String =
        if (precipitationMm <= KiteSectionProvider.DRY_THRESHOLD_MM) "droog" else "${precipitationMm} mm nat"

    private fun tideText(slot: SlotAssessment): String {
        val at = slot.nearestLowTideAt
        val nabijheid = if (slot.nearLowTide) "dichtbij laagwater" else "niet dichtbij laagwater"
        if (at == null) return nabijheid
        val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam")).format(at)
        return "$nabijheid, laagwater om $time"
    }
}
