package nl.vdzon.robbertsassistent.assistant

import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantServiceTest {
    @Test
    fun `always replies with the dummy acknowledgement`() {
        val service = AssistantService()

        assertEquals("Ga ik doen", service.reply("wat is de wind"))
        assertEquals("Ga ik doen", service.reply("iets heel anders"))
    }
}
