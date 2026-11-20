package nl.vdzon.robbertsassistent.zonneplan

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolarModelTest {
    private val today: LocalDate = LocalDate.parse("2026-11-20")

    /** Wisselend weer: afwisselend zonnige en bewolkte dagen, zodat beide factoren te bepalen zijn. */
    private fun weather(days: Int): Map<LocalDate, DayRadiation> =
        (0L..days).associate { back ->
            val sunny = back % 3 != 0L
            today.minusDays(back) to DayRadiation(
                diffuseKwhM2 = if (sunny) 0.6 else 0.4,
                directKwhM2 = if (sunny) 1.2 + (back % 5) * 0.1 else 0.05,
                sunshineHours = if (sunny) 6.0 else 0.3,
            )
        }

    /** Panelen die 4 kWh per kWh/m² diffuus en 2 per kWh/m² direct leveren. */
    private fun yields(weather: Map<LocalDate, DayRadiation>, factor: (LocalDate) -> Double = { 1.0 }) =
        weather.mapValues { (day, w) -> (4 * w.diffuseKwhM2 + 2 * w.directKwhM2) * factor(day) }

    @Test
    fun `fit vindt de twee factoren terug en houdt ze niet-negatief`() {
        val w = weather(30).values.toList()
        val (a, b) = SolarModel.fit(w.map { Triple(4 * it.diffuseKwhM2 + 2 * it.directKwhM2, it.diffuseKwhM2, it.directKwhM2) })
        assertEquals(4.0, a, 0.01)
        assertEquals(2.0, b, 0.01)

        // Alleen diffuus licht levert iets op (schaduw in de winter): direct mag dan niet negatief uitvallen.
        val (a2, b2) = SolarModel.fit(w.map { Triple(4 * it.diffuseKwhM2 - 0.1 * it.directKwhM2, it.diffuseKwhM2, it.directKwhM2) })
        assertTrue(a2 > 0)
        assertEquals(0.0, b2)
    }

    @Test
    fun `opbrengst volgens het eigen patroon is goed, met een verwachting per dag`() {
        val w = weather(60)
        val overview = SolarModel.assess(yields(w), w, today)

        assertEquals(SolarVerdict.GOED, overview.verdict)
        assertEquals(100, overview.weekPercent)
        assertEquals(61, overview.days.size)
        // De eerste dagen hebben nog geen dertig dagen geschiedenis en dus geen verwachting.
        assertNull(overview.days.first().expectedKwh)
        val yesterday = overview.days.first { it.date == today.minusDays(1) }
        assertEquals(yesterday.yieldKwh!!, yesterday.expectedKwh!!, 0.02)
        // Vandaag loopt nog en telt niet mee in het oordeel.
        assertEquals(false, overview.days.last().complete)
        assertTrue(overview.verdictText.contains("7 dagen"))
    }

    @Test
    fun `een week op minder dan de helft is let op, op minder dan een derde niet goed`() {
        val w = weather(60)
        val lastWeek = { factor: Double -> { day: LocalDate -> if (day.isAfter(today.minusDays(8))) factor else 1.0 } }

        assertEquals(SolarVerdict.LET_OP, SolarModel.assess(yields(w, lastWeek(0.4)), w, today).verdict)
        assertEquals(SolarVerdict.NIET, SolarModel.assess(yields(w, lastWeek(0.2)), w, today).verdict)
        // Zestig procent valt nog binnen wat het weermodel per week kan afwijken.
        assertEquals(SolarVerdict.GOED, SolarModel.assess(yields(w, lastWeek(0.6)), w, today).verdict)
    }

    @Test
    fun `gisteren niets opgewekt op een dag met zon is meteen niet goed`() {
        val w = weather(60)
        val overview = SolarModel.assess(yields(w) { if (it == today.minusDays(1)) 0.0 else 1.0 }, w, today)

        assertEquals(SolarVerdict.NIET, overview.verdict)
        assertTrue(overview.verdictText.contains("vrijwel niets opgewekt"))
    }

    @Test
    fun `met te weinig dagen is er geen oordeel`() {
        val w = weather(10)
        val overview = SolarModel.assess(yields(w), w, today)

        assertEquals(SolarVerdict.ONBEKEND, overview.verdict)
        assertNull(overview.weekPercent)
        assertEquals(SolarVerdict.ONBEKEND, SolarModel.assess(emptyMap(), w, today).verdict)
    }

    @Test
    fun `dagen zonder weergegevens tellen niet mee maar staan wel in de grafiek`() {
        val w = weather(60)
        val overview = SolarModel.assess(yields(w), w - today.minusDays(2), today)

        val gap = overview.days.first { it.date == today.minusDays(2) }
        assertNotNull(gap.yieldKwh)
        assertNull(gap.expectedKwh)
        assertEquals(SolarVerdict.GOED, overview.verdict)
    }

    @Test
    fun `service combineert opbrengst en weer, en een storing geeft een melding in plaats van een fout`() {
        val w = weather(60)
        val clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC)
        val zonneplan = object : ZonneplanClient {
            var calls = 0
            override fun status() = SolarStatusResult(currentPowerWatt = 420, yesterdayYieldKwh = 2.0)
            override fun dailyYields(since: LocalDate): Map<LocalDate, Double> {
                calls++
                return yields(w)
            }
        }
        val service = SolarService(zonneplan, object : SolarRadiationClient {
            override fun daily(from: LocalDate, to: LocalDate) = w
        }, clock)

        val overview = service.overview()
        assertEquals(SolarVerdict.GOED, overview.verdict)
        assertEquals(420, overview.currentPowerWatt)
        service.overview()
        assertEquals(1, zonneplan.calls, "de tweede aanvraag komt uit de cache")

        val broken = SolarService(zonneplan, object : SolarRadiationClient {
            override fun daily(from: LocalDate, to: LocalDate): Map<LocalDate, DayRadiation> = error("geen verbinding")
        }, clock).overview()
        assertEquals(SolarVerdict.ONBEKEND, broken.verdict)
        assertEquals("geen verbinding", broken.error)
    }

    @Test
    fun `uurwaarden van Open-Meteo worden kWh per dag, met zonuren`() {
        val json = jacksonObjectMapper().readTree(
            """
            {"hourly": {"time": ["2026-11-19T10:00", "2026-11-19T11:00", "2026-11-20T10:00"],
                        "diffuse_radiation": [100.0, 150.0, 80.0],
                        "direct_radiation": [300.0, null, 20.0]},
             "daily": {"time": ["2026-11-19", "2026-11-20"], "sunshine_duration": [7200.0, null]}}
            """,
        )

        val days = OpenMeteoSolarRadiationClient.parse(json)

        val first = days.getValue(LocalDate.parse("2026-11-19"))
        assertEquals(0.25, first.diffuseKwhM2, 1e-9)
        assertEquals(0.3, first.directKwhM2, 1e-9)
        assertEquals(2.0, first.sunshineHours)
        assertNull(days.getValue(LocalDate.parse("2026-11-20")).sunshineHours)
    }

    @Test
    fun `dagstatistieken van Home Assistant worden opbrengst per Nederlandse dag`() {
        // 1795042800000 = 19 nov 2026 00:00 in Nederland (23:00 UTC de dag ervoor).
        val json = jacksonObjectMapper().readTree(
            """
            {"sensor.zonneplan_opbrengst_vandaag": [
               {"start": 1795042800000, "end": 1795129200000, "change": 1.3799999999991996},
               {"start": 1795129200000, "end": 1795215600000, "change": 2.6299999999996544},
               {"start": 1795215600000, "end": 1795302000000, "change": null},
               {"start": 1795302000000, "end": 1795388400000, "change": -3.0}
             ]}
            """,
        )

        val yields = HomeAssistantZonneplanClient.parseDailyYields(json, "sensor.zonneplan_opbrengst_vandaag")

        assertEquals(mapOf(LocalDate.parse("2026-11-19") to 1.38, LocalDate.parse("2026-11-20") to 2.63), yields)
    }
}
