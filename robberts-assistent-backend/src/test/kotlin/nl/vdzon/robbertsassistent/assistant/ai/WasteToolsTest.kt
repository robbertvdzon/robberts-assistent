package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.waste.StubWasteClient
import kotlin.test.Test
import kotlin.test.assertTrue

class WasteToolsTest {

    @Test
    fun `getWastePickups geeft de ophaalmomenten op basis van de stub`() {
        val tools = WasteTools(StubWasteClient())

        val result = tools.getWastePickups()

        assertTrue(result.contains("gft & etensresten"), result)
        assertTrue(result.contains("plastic, blik & drinkpakken"), result)
    }
}
