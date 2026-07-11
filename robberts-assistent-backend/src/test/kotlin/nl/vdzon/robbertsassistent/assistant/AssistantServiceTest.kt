package nl.vdzon.robbertsassistent.assistant

import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantServiceTest {
    @Test
    fun `always replies with the dummy acknowledgement`() {
        val service = AssistantService()

        assertEquals("Doe ik", service.reply("wat is de wind"))
        assertEquals("Doe ik", service.reply("iets heel anders"))
    }
}
