package nl.vdzon.robbertsassistent.airquality

import java.time.Instant

/** Eén uur luchtkwaliteit/UV/pollen. */
data class HourlyAirQuality(
    val time: Instant,
    val europeanAqi: Int?,
    val pm10: Double?,
    val pm25: Double?,
    val uvIndex: Double?,
    val birchPollen: Double?,
    val grassPollen: Double?,
    val ragweedPollen: Double?,
)

/**
 * Resultaat van een luchtkwaliteit-ophaal-poging. Bij een netwerk-/serverfout is [hours] leeg en
 * [error] gezet — de aanroeper (`AirQualityTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class AirQualityForecast(
    val hours: List<HourlyAirQuality>,
    val error: String? = null,
)

/**
 * Luchtkwaliteit/UV/pollen-voorspelling bij de moestuin (Luttik Cie 12, Heemskerk) — zelfde
 * locatie als de regen-/weersvoorspelling. Fase 0 (keyless, geen secret nodig):
 * [OpenMeteoAirQualityClient] is de enige, altijd-actieve implementatie. [StubAirQualityClient]
 * bestaat alleen voor tests, zodat tools zonder netwerk-call getest kunnen worden.
 */
interface AirQualityClient {
    /** Uurvoorspelling vanaf nu, oplopend in tijd, voor maximaal [hours] uur vooruit. */
    fun hourlyForecast(hours: Int = 24): AirQualityForecast
}

/** Nederlandse omschrijving van de Europese luchtkwaliteitsindex (EAQI). */
internal fun europeanAqiDescription(aqi: Int): String = when {
    aqi <= 20 -> "goed"
    aqi <= 40 -> "redelijk"
    aqi <= 60 -> "matig"
    aqi <= 80 -> "slecht"
    aqi <= 100 -> "zeer slecht"
    else -> "extreem slecht"
}

/** Nederlandse omschrijving van de UV-index (WHO-schaal). */
internal fun uvIndexDescription(uvIndex: Double): String = when {
    uvIndex < 3 -> "laag"
    uvIndex < 6 -> "matig"
    uvIndex < 8 -> "hoog"
    uvIndex < 11 -> "zeer hoog"
    else -> "extreem"
}
