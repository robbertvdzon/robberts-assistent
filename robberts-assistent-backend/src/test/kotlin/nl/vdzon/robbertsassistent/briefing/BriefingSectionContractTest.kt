package nl.vdzon.robbertsassistent.briefing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BriefingSectionContractTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `oude JSON zonder tegelvelden blijft deserialiseren`() {
        val section = objectMapper.readValue<BriefingSection>(
            """{"key":"agenda","title":"Agenda","text":"Geen afspraken.","items":[]}""",
        )

        assertNull(section.status)
        assertNull(section.tileLabel)
    }

    @Test
    fun `status en tegeltekst krijgen de afgesproken JSON-waarden`() {
        val json = objectMapper.writeValueAsString(
            BriefingSection(
                key = "kite",
                title = "Kiten",
                text = "details",
                status = BriefingStatus.LET_OP,
                tileLabel = "17 kn W",
            ),
        )

        assertTrue(json.contains("\"status\":\"LET_OP\""))
        assertTrue(json.contains("\"tileLabel\":\"17 kn W\""))
        assertEquals(BriefingStatus.LET_OP, objectMapper.readValue<BriefingSection>(json).status)
    }
}
