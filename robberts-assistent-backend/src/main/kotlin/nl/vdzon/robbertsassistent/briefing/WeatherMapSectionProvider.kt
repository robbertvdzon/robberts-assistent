package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WindForecastClient
import nl.vdzon.robbertsassistent.weather.weatherCodeDescription
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Weerkaart-briefingsectie voor morgen: twee kaartbeelden van de kust IJmuiden-Egmond (09:00 en
 * 14:00), elk met windrichtingspijl, windsnelheid (kn) en weer-icoon — zie [CoastMapImageBuilder].
 * Staat bovenaan de briefing (`order = -10`, boven [KiteSectionProvider] en
 * [BeachCycleSectionProvider]), puur SPI dus geen wijziging aan [BriefingService]/
 * `BriefingController` nodig. De gegenereerde PNG's worden opgeslagen via [WeatherMapStorage] (vaste
 * sleutels `ochtend`/`middag`) en ontsloten via `GET /api/v1/briefing/weather-map/{slot}`; deze
 * sectie levert alleen de relatieve `imageUrl` (zie [BriefingItem]). Faalt de wind- of
 * weervoorspelling, dan levert de sectie een nette foutmelding (geen crash van de hele briefing).
 */
@Component
class WeatherMapSectionProvider(
    private val windForecastClient: WindForecastClient,
    private val weatherClient: WeatherClient,
    private val coastMapImageBuilder: CoastMapImageBuilder,
    private val weatherMapStorage: WeatherMapStorage,
) : BriefingSectionProvider {

    override val order = -10

    override fun section(): BriefingSection {
        val wind = windForecastClient.hourlyForecast(48)
        val weather = weatherClient.hourlyForecast(48)
        val error = wind.error ?: weather.error
        if (error != null) {
            return BriefingSection(key = KEY, title = TITLE, text = "Kon de weerkaart voor morgen niet opbouwen: $error")
        }

        val zone = ZoneId.of("Europe/Amsterdam")
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val slots = listOf(
            Slot(key = "ochtend", label = "Ochtend", at = tomorrow.atTime(9, 0)),
            Slot(key = "middag", label = "Middag", at = tomorrow.atTime(14, 0)),
        )

        val items = slots.mapNotNull { slot ->
            val at = slot.at.atZone(zone).toInstant()
            val windHour = wind.hours.minByOrNull { Duration.between(it.time, at).abs() } ?: return@mapNotNull null
            val weatherHour = weather.hours.minByOrNull { Duration.between(it.time, at).abs() } ?: return@mapNotNull null
            val png = coastMapImageBuilder.build(windHour.speedKn, windHour.directionDeg, weatherHour.weatherCode)
            weatherMapStorage.store(slot.key, png)
            val direction = KiteSectionProvider.compassPoint(windHour.directionDeg)
            BriefingItem(
                text = "${slot.label}: ${windHour.speedKn.toInt()} kn ($direction), " +
                    weatherCodeDescription(weatherHour.weatherCode),
                imageUrl = "/api/v1/briefing/weather-map/${slot.key}",
            )
        }

        if (items.isEmpty()) {
            return BriefingSection(key = KEY, title = TITLE, text = "Geen voorspellingsdata beschikbaar voor de weerkaart.")
        }
        return BriefingSection(key = KEY, title = TITLE, text = "", items = items)
    }

    private data class Slot(val key: String, val label: String, val at: java.time.LocalDateTime)

    private companion object {
        const val KEY = "weather-map"
        const val TITLE = "Weerkaart"
    }
}
