package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.softwarefactory.StubSoftwareFactoryClient
import kotlin.test.Test
import kotlin.test.assertTrue

class SoftwareFactoryToolsTest {

    @Test
    fun `getFactoryStories geeft de stories op basis van de stub`() {
        val tools = SoftwareFactoryTools(StubSoftwareFactoryClient())

        val result = tools.getFactoryStories()

        assertTrue(result.contains("SF-1"), result)
        assertTrue(result.contains("gemerged"), result)
    }

    @Test
    fun `getFactoryMyActions geeft de actiepunten op basis van de stub`() {
        val tools = SoftwareFactoryTools(StubSoftwareFactoryClient())

        val result = tools.getFactoryMyActions()

        assertTrue(result.contains("Mag ik dit zo mergen?"), result)
    }
}
