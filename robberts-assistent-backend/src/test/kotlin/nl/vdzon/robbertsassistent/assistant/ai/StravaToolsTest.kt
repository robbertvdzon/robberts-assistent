package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.strava.StubStravaClient
import kotlin.test.Test
import kotlin.test.assertTrue

class StravaToolsTest {

    @Test
    fun `getRecentActivities geeft de trainingen op basis van de stub`() {
        val tools = StravaTools(StubStravaClient())

        val result = tools.getRecentActivities()

        assertTrue(result.contains("Ochtendrit"), result)
        assertTrue(result.contains("indoor"), result)
        assertTrue(result.contains("Rondje Heemskerk"), result)
        assertTrue(result.contains("8.2 km"), result)
    }
}
