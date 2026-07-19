package nl.vdzon.robbertsassistent.tides

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseForecast`/`extremesOf`-conversie zonder HTTP — geen precedent in deze repo
 * voor het mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`).
 */
class RwsTideClientTest {

    @Test
    fun `parseForecast pakt alleen de verwachting-reeks en sorteert oplopend`() {
        val json = jacksonObjectMapper().readTree(
            """
            {
              "WaarnemingenLijst": [
                {"AquoMetadata": {"ProcesType": "meting"}, "MetingenLijst": [
                  {"Tijdstip": "2026-07-19T10:00:00.000+02:00", "Meetwaarde": {"Waarde_Numeriek": 999.0}}
                ]},
                {"AquoMetadata": {"ProcesType": "verwachting"}, "MetingenLijst": [
                  {"Tijdstip": "2026-07-19T10:20:00.000+02:00", "Meetwaarde": {"Waarde_Numeriek": 10.0}},
                  {"Tijdstip": "2026-07-19T10:00:00.000+02:00", "Meetwaarde": {"Waarde_Numeriek": 5.0}},
                  {"Tijdstip": "2026-07-19T10:10:00.000+02:00", "Meetwaarde": {"Waarde_Numeriek": 8.0}}
                ]}
              ]
            }
            """.trimIndent(),
        )

        val forecast = RwsTideClient.parseForecast(json)

        assertNull(forecast.error)
        assertEquals(listOf(5, 8, 10), forecast.levels.map { it.heightCm })
    }

    @Test
    fun `parseForecast geeft duidelijke melding als de verwachting-reeks ontbreekt`() {
        val json = jacksonObjectMapper().readTree(
            """{"WaarnemingenLijst": [{"AquoMetadata": {"ProcesType": "meting"}, "MetingenLijst": []}]}""",
        )

        val forecast = RwsTideClient.parseForecast(json)

        assertTrue(forecast.levels.isEmpty())
        assertEquals("RWS gaf geen getijvoorspelling terug.", forecast.error)
    }

    @Test
    fun `extremesOf herkent hoog- en laagwater in een synthetische getijcurve, netjes afwisselend`() {
        val extremes = RwsTideClient.extremesOf(sineWaveLevels())

        assertEquals(5, extremes.size, "$extremes")
        extremes.zipWithNext().forEach { (a, b) -> assertTrue(a.type != b.type, "extremen moeten afwisselen: $extremes") }
        assertEquals(TideType.HOOGWATER, extremes[0].type)
        assertTrue(extremes.all { it.heightCm in -85..85 }, "$extremes")
    }

    @Test
    fun `extremesOf negeert kleine ruis rond een echt extreem (agger-achtige plateau)`() {
        val levels = sineWaveLevels().mapIndexed { i, level ->
            val minutes = i * 10.0
            if (minutes in 180.0..240.0) level.copy(heightCm = level.heightCm + if (i % 2 == 0) 2 else -2) else level
        }

        val extremes = RwsTideClient.extremesOf(levels)

        assertEquals(5, extremes.size, "de ruis zou geen extra extremen mogen opleveren: $extremes")
        extremes.zipWithNext().forEach { (a, b) -> assertTrue(a.type != b.type, "extremen moeten afwisselen: $extremes") }
    }

    @Test
    fun `extremesOf telt het eerste of laatste punt van de reeks nooit als extreem`() {
        val levels = sineWaveLevels()

        val extremes = RwsTideClient.extremesOf(levels)

        assertTrue(extremes.none { it.time == levels.first().time || it.time == levels.last().time }, "$extremes")
    }

    /** 30 uur, elke 10 minuten, een realistisch semidiurnaal getij (periode ≈ 12u25). */
    private fun sineWaveLevels(): List<WaterLevel> {
        val start = Instant.parse("2026-07-19T00:00:00Z")
        val periodMinutes = 745.0
        return (0 until 30 * 6).map { i ->
            val minutes = i * 10.0
            val height = (80 * sin(2 * PI * minutes / periodMinutes)).roundToInt()
            WaterLevel(start.plusSeconds((minutes * 60).toLong()), height)
        }
    }
}
