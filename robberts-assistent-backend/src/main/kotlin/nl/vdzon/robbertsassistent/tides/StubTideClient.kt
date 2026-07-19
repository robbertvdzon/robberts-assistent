package nl.vdzon.robbertsassistent.tides

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Vaste, deterministische getijcurve (één hoog- en één laagwater in de eerste 12 uur) — puur voor
 * tests, zodat `TideTools` zonder netwerk-call getest kan worden (zelfde patroon als
 * `StubCalendarClient`). Niet als Spring-bean geregistreerd: [RwsTideClient] is keyless en dus
 * altijd actief.
 */
class StubTideClient : TideClient {
    override fun forecast(hours: Int): TideForecast {
        // Op het hele uur afgerond, zodat `TideTools.getWaterLevelForecast` (die op het hele uur
        // filtert) hier ook zonder een echte, op tijdstippen uitgelijnde bron waarden voor vindt.
        val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
        val levels = (0 until hours).map { offset ->
            WaterLevel(time = now.plus(Duration.ofHours(offset.toLong())), heightCm = heightAt(offset))
        }
        val extremes = listOf(
            TideExtreme(now.plus(Duration.ofHours(3)), heightAt(3), TideType.HOOGWATER),
            TideExtreme(now.plus(Duration.ofHours(9)), heightAt(9), TideType.LAAGWATER),
        ).filter { it.time.isBefore(now.plus(Duration.ofHours(hours.toLong()))) }
        return TideForecast(levels, extremes)
    }

    /** Simpele blokvorm: stijgt tot uur 3 (hoogwater, 100 cm), daalt tot uur 9 (laagwater, -80 cm). */
    private fun heightAt(hour: Int): Int {
        val cycleHour = hour % 12
        return when {
            cycleHour <= 3 -> -80 + (cycleHour * 60)
            cycleHour <= 9 -> 100 - ((cycleHour - 3) * 30)
            else -> -80 + ((cycleHour - 9) * 60)
        }
    }
}
