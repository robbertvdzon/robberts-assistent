package nl.vdzon.robbertsassistent.tides

import java.time.Instant

/** Eén moment uit de getijvoorspelling. */
data class WaterLevel(
    val time: Instant,
    val heightCm: Int,
)

enum class TideType { HOOGWATER, LAAGWATER }

/** Eén hoog- of laagwatermoment, afgeleid uit de voorspellingscurve. */
data class TideExtreme(
    val time: Instant,
    val heightCm: Int,
    val type: TideType,
)

/**
 * Resultaat van een getij-ophaal-poging. Bij een netwerk-/serverfout zijn [levels] en [extremes]
 * leeg en [error] gezet — de aanroeper (`TideTools`) degradeert dan netjes naar een duidelijke
 * melding in plaats van te crashen.
 */
data class TideForecast(
    val levels: List<WaterLevel>,
    val extremes: List<TideExtreme>,
    val error: String? = null,
)

/**
 * Getijvoorspelling (waterhoogte t.o.v. NAP) bij IJmuiden, buitenhaven — zelfde locatie als de
 * bestaande wind-tool, relevant voor stranduitjes/kite. Fase 0 (keyless, geen secret nodig):
 * [RwsTideClient] is de enige, altijd-actieve implementatie. [StubTideClient] bestaat alleen voor
 * tests, zodat tools zonder netwerk-call getest kunnen worden (zelfde patroon als `StubCalendarClient`).
 */
interface TideClient {
    /** Waterhoogte-voorspelling vanaf nu, oplopend in tijd, voor maximaal [hours] uur vooruit. */
    fun forecast(hours: Int = 48): TideForecast
}
