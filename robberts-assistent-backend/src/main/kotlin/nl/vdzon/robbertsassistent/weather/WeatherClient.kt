package nl.vdzon.robbertsassistent.weather

import java.time.Instant

/** Eén uur uit de weersvoorspelling. */
data class HourlyWeather(
    val time: Instant,
    val temperatureC: Double,
    val precipitationMm: Double,
    val precipitationProbabilityPct: Int?,
    val weatherCode: Int,
)

/**
 * Resultaat van een voorspelling-ophaal-poging. Bij een netwerk-/serverfout is [hours] leeg en
 * [error] gezet — de aanroeper (zie `WeatherTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class WeatherForecast(
    val hours: List<HourlyWeather>,
    val error: String? = null,
)

/**
 * Weersvoorspelling voor de thuislocatie (IJmuiden, zelfde coördinaten als [WindTools][nl.vdzon.robbertsassistent.assistant.ai.WindTools]).
 * Fase 0 (keyless, geen secret nodig): [OpenMeteoWeatherClient] is de enige, altijd-actieve
 * implementatie. [StubWeatherClient] bestaat alleen voor tests, zodat tools zonder netwerk-call
 * getest kunnen worden (zelfde patroon als `StubCalendarClient`).
 */
interface WeatherClient {
    /** Uurvoorspelling vanaf nu, oplopend in tijd, voor maximaal [hours] uur vooruit (max. 72). */
    fun hourlyForecast(hours: Int = 24): WeatherForecast
}

/** Nederlandse omschrijving van een WMO-weathercode (zoals Open-Meteo die teruggeeft). */
internal fun weatherCodeDescription(code: Int): String = when (code) {
    0 -> "helder"
    1 -> "overwegend helder"
    2 -> "half bewolkt"
    3 -> "bewolkt"
    45, 48 -> "mist"
    51, 53, 55 -> "motregen"
    56, 57 -> "onderkoelde motregen"
    61, 63, 65 -> "regen"
    66, 67 -> "onderkoelde regen"
    71, 73, 75 -> "sneeuw"
    77 -> "sneeuwkorrels"
    80, 81, 82 -> "regenbuien"
    85, 86 -> "sneeuwbuien"
    95 -> "onweer"
    96, 99 -> "onweer met hagel"
    else -> "onbekend ($code)"
}
