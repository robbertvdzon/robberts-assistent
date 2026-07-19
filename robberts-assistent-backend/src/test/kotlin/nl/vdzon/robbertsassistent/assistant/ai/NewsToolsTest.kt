package nl.vdzon.robbertsassistent.assistant.ai

import nl.vdzon.robbertsassistent.news.StubNewsClient
import kotlin.test.Test
import kotlin.test.assertTrue

class NewsToolsTest {

    @Test
    fun `getLatestNews geeft de nieuwsitems op basis van de stub`() {
        val tools = NewsTools(StubNewsClient())

        val result = tools.getLatestNews()

        assertTrue(result.contains("Voorbeeldnieuws één"), result)
        assertTrue(result.contains("https://voorbeeld.nl/1"), result)
    }
}
