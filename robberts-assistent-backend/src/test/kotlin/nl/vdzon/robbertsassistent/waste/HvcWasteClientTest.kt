package nl.vdzon.robbertsassistent.waste

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekt de pure `parseSchedule`-conversie zonder HTTP — geen precedent in deze repo voor het
 * mocken van `java.net.http.HttpClient` (zie o.a. `WindToolsTest`).
 */
class HvcWasteClientTest {

    @Test
    fun `parseSchedule houdt alleen items met een echte ophaaldatum over, oplopend gesorteerd`() {
        val json = jacksonObjectMapper().readTree(
            """
            [
              {"id": 6, "parent_id": 0, "title": "plastic, blik & drinkpakken", "ophaaldatum": "2026-07-23"},
              {"id": 51, "parent_id": 6, "title": "Plastic gemeente Heemskerk", "ophaaldatum": null},
              {"id": 5, "parent_id": 0, "title": "gft & etensresten", "ophaaldatum": "2026-07-21"}
            ]
            """.trimIndent(),
        )

        val schedule = HvcWasteClient.parseSchedule(json)

        assertNull(schedule.error)
        assertEquals(2, schedule.pickups.size)
        assertEquals("gft & etensresten", schedule.pickups[0].type)
        assertEquals(LocalDate.of(2026, 7, 21), schedule.pickups[0].date)
        assertEquals("plastic, blik & drinkpakken", schedule.pickups[1].type)
    }

    @Test
    fun `parseSchedule geeft duidelijke melding als de response geen array is`() {
        val json = jacksonObjectMapper().readTree("""{"error": "onverwacht"}""")

        val schedule = HvcWasteClient.parseSchedule(json)

        assertTrue(schedule.pickups.isEmpty())
        assertEquals("HVC gaf geen afvalkalender terug.", schedule.error)
    }

    @Test
    fun `parseSchedule geeft een lege lijst (geen fout) als geen enkel item een ophaaldatum heeft`() {
        val json = jacksonObjectMapper().readTree(
            """[{"id": 51, "title": "Info-pagina", "ophaaldatum": null}]""",
        )

        val schedule = HvcWasteClient.parseSchedule(json)

        assertNull(schedule.error)
        assertTrue(schedule.pickups.isEmpty())
    }
}
