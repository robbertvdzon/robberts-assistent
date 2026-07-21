package nl.vdzon.robbertsassistent.briefing

import nl.vdzon.robbertsassistent.tides.TideClient
import nl.vdzon.robbertsassistent.tides.TideExtreme
import nl.vdzon.robbertsassistent.weather.WeatherClient
import nl.vdzon.robbertsassistent.weather.WindForecastClient
import nl.vdzon.robbertsassistent.weather.weatherCodeDescription
import org.springframework.stereotype.Component
import java.awt.Color
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Weerkaart-briefingsectie voor morgen: één kaartbeeld van de kust IJmuiden-Egmond met daarover
 * twee windpijlen (07:00 en 19:00) in verschillende kleuren, elk met windsnelheid (kn) en een
 * echt getekend weer-icoon, plus een legenda en onderin een dag-breed weersymbool + de
 * hoog-/laagwatertijden (IJmuiden) — zie [CoastMapImageBuilder]. Staat bovenaan de briefing
 * (`order = -10`, boven [KiteSectionProvider] en [BeachCycleSectionProvider]), puur SPI dus geen
 * wijziging aan [BriefingService]/`BriefingController` nodig. Het gegenereerde PNG wordt
 * opgeslagen via [WeatherMapStorage] (vaste sleutel `morgen`) en ontsloten via
 * `GET /api/v1/briefing/weather-map/{slot}`; deze sectie levert precies één `BriefingItem` met de
 * relatieve `imageUrl` (zie [BriefingItem]). Faalt de wind- of weervoorspelling, of ontbreekt data
 * voor een van beide dagdelen, dan levert de sectie een nette foutmelding (geen crash van de hele
 * briefing). Faalt alleen de getijvoorspelling, of ontbreken er getijmomenten voor morgen, dan
 * blijft de kaart wel opgebouwd — alleen zonder getijtijden onderin.
 */
@Component
class WeatherMapSectionProvider(
    private val windForecastClient: WindForecastClient,
    private val weatherClient: WeatherClient,
    private val tideClient: TideClient,
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
        val daySlots = listOf(
            DaySlot(label = "Ochtend", color = Color(0xF5, 0x7C, 0x00), at = tomorrow.atTime(7, 0)),
            DaySlot(label = "Avond", color = Color(0x15, 0x65, 0xC0), at = tomorrow.atTime(19, 0)),
        )

        val descriptions = mutableListOf<String>()
        val mapSlots = daySlots.mapNotNull { slot ->
            val at = slot.at.atZone(zone).toInstant()
            val windHour = wind.hours.minByOrNull { Duration.between(it.time, at).abs() } ?: return@mapNotNull null
            val weatherHour = weather.hours.minByOrNull { Duration.between(it.time, at).abs() } ?: return@mapNotNull null
            val direction = KiteSectionProvider.compassPoint(windHour.directionDeg)
            descriptions += "${slot.label}: ${windHour.speedKn.toInt()} kn ($direction), " +
                weatherCodeDescription(weatherHour.weatherCode)
            WindMapSlot(
                label = slot.label,
                color = slot.color,
                speedKn = windHour.speedKn,
                directionDeg = windHour.directionDeg,
                weatherCode = weatherHour.weatherCode,
            )
        }

        if (mapSlots.isEmpty()) {
            return BriefingSection(key = KEY, title = TITLE, text = "Geen voorspellingsdata beschikbaar voor de weerkaart.")
        }

        val dayWeatherCode = mapSlots.first().weatherCode
        val tideExtremes = tomorrowTideExtremes(tomorrow, zone)

        val png = coastMapImageBuilder.build(mapSlots, dayWeatherCode, tideExtremes)
        weatherMapStorage.store(STORAGE_KEY, png)
        val item = BriefingItem(
            text = descriptions.joinToString(" · "),
            imageUrl = "/api/v1/briefing/weather-map/$STORAGE_KEY",
        )
        return BriefingSection(key = KEY, title = TITLE, text = "", items = listOf(item))
    }

    /** Levert een lege lijst (in plaats van te crashen) bij een getij-fout of ontbrekende data voor morgen. */
    private fun tomorrowTideExtremes(tomorrow: LocalDate, zone: ZoneId): List<TideExtreme> = runCatching {
        val tide = tideClient.forecast(48)
        if (tide.error != null) return@runCatching emptyList()
        tide.extremes.filter { it.time.atZone(zone).toLocalDate() == tomorrow }
    }.getOrDefault(emptyList())

    private data class DaySlot(val label: String, val color: Color, val at: java.time.LocalDateTime)

    private companion object {
        const val KEY = "weather-map"
        const val TITLE = "Weerkaart"
        const val STORAGE_KEY = "morgen"
    }
}
