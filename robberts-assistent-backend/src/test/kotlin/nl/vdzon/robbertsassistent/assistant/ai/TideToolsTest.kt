package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.tides.StubTideClient
import kotlin.test.Test
import kotlin.test.assertTrue

class TideToolsTest {

    @Test
    fun `getWaterLevelForecast geeft uurwaarden op basis van de stub`() {
        val tools = TideTools(StubTideClient())

        val result = tools.getWaterLevelForecast()

        assertTrue(result.contains("cm t.o.v. NAP"), result)
        assertTrue(result.isNotBlank(), result)
    }

    @Test
    fun `getTideExtremes geeft hoog- en laagwater op basis van de stub`() {
        val tools = TideTools(StubTideClient())

        val result = tools.getTideExtremes()

        assertTrue(result.contains("hoogwater"), result)
        assertTrue(result.contains("laagwater"), result)
    }
}
